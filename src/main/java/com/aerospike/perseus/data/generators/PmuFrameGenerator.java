package com.aerospike.perseus.data.generators;

import com.aerospike.client.Value;
import com.aerospike.perseus.data.PmuSourceData;
import com.aerospike.perseus.data.PmuFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates PMU frames by replaying real Hitachi 27-bus CSV data in a loop.
 *
 * Loads at startup:
 *   - PMU_27bus_SingleSnapshot.json → source mapping (AssignDevice)
 *   - Flat_Complex_RealValue.csv, Flat_Complex_ImagValue.csv, Flat_Complex_Quality.csv
 *   - Flat_Real_Value.csv, Flat_Real_Quality.csv
 *
 * Data model per source record (matches Hitachi app exactly):
 *   Key:  "{streamId}:{sourceId}:{timestampMicros}"
 *   Bin "cx": KEY_ORDERED map { Long globalIndex → List[real, imag, quality] }
 *   Bin "rl": KEY_ORDERED map { Long globalIndex → List[value, quality] }
 *
 * 27-bus: 6 sources, 137 complex, 144 real, 36001 columns (180s at 200 FPS).
 * Columns cycle on repeat when the 180s recording is exhausted.
 */
public class PmuFrameGenerator extends BaseGenerator<PmuFrame> {

    private final int streamCount;
    private final String streamPrefix;
    private final int streamStartIndex;
    private final long intervalMicros;

    // Per-stream timestamp and column tracking
    private final AtomicLong[] nextTimestamps;
    private final AtomicLong[] columnCounters;
    private final AtomicLong streamCounter = new AtomicLong(0);

    // Source mapping from JSON (matches Hitachi SourceMapping.fromMetadata)
    private final List<String> sourceIds;
    private final Map<String, List<Integer>> complexBySource;
    private final Map<String, List<Integer>> realBySource;

    // CSV data matrices (row=measurement, col=timestamp)
    private final double[][] cxReal;
    private final double[][] cxImag;
    private final int[][] cxQuality;
    private final double[][] rlValue;
    private final int[][] rlQuality;
    private final int numColumns;

    public PmuFrameGenerator(int streamCount, String streamPrefix, int streamStartIndex,
                             int complexCount, int realCount, int fps) {
        this.streamCount = streamCount;
        this.streamPrefix = streamPrefix;
        this.streamStartIndex = streamStartIndex;
        this.intervalMicros = 1_000_000L / fps;

        System.out.println("Loading PMU data from classpath resources...");
        long loadStart = System.currentTimeMillis();

        // Load source mapping from JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("pmudata/PMU_27bus_SingleSnapshot.json");
            if (jsonStream == null) throw new RuntimeException("PMU_27bus_SingleSnapshot.json not found on classpath");
            JsonNode root = mapper.readTree(jsonStream);

            List<Integer> cxAssignDevice = jsonArrayToIntList(root.get("Complex").get("AssignDevice"));
            List<Integer> rlAssignDevice = jsonArrayToIntList(root.get("Real").get("AssignDevice"));

            // Build source mapping (same logic as Hitachi SourceMapping.fromMetadata)
            complexBySource = new LinkedHashMap<>();
            for (int i = 0; i < cxAssignDevice.size(); i++) {
                String srcId = "PMU_" + cxAssignDevice.get(i);
                complexBySource.computeIfAbsent(srcId, k -> new ArrayList<>()).add(i);
            }

            realBySource = new LinkedHashMap<>();
            for (int i = 0; i < rlAssignDevice.size(); i++) {
                String srcId = "PMU_" + rlAssignDevice.get(i);
                realBySource.computeIfAbsent(srcId, k -> new ArrayList<>()).add(i);
            }

            Set<String> allSrcs = new LinkedHashSet<>();
            allSrcs.addAll(complexBySource.keySet());
            allSrcs.addAll(realBySource.keySet());
            sourceIds = new ArrayList<>(allSrcs);

            System.out.println("Source mapping: " + sourceIds.size() + " sources, " +
                    complexCount + " complex, " + realCount + " real measurements");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load PMU JSON metadata", e);
        }

        // Load CSV data matrices
        try {
            cxReal = readCsvMatrix("pmudata/Flat_Complex_RealValue.csv", complexCount);
            cxImag = readCsvMatrix("pmudata/Flat_Complex_ImagValue.csv", complexCount);
            cxQuality = readCsvIntMatrix("pmudata/Flat_Complex_Quality.csv", complexCount);
            rlValue = readCsvMatrix("pmudata/Flat_Real_Value.csv", realCount);
            rlQuality = readCsvIntMatrix("pmudata/Flat_Real_Quality.csv", realCount);
            numColumns = cxReal[0].length;

            System.out.println("CSV data loaded: " + numColumns + " columns (timestamps) in " +
                    (System.currentTimeMillis() - loadStart) + "ms");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load PMU CSV data", e);
        }

        // Initialize per-stream timestamps from now
        long baseMicros = System.currentTimeMillis() * 1000;
        this.nextTimestamps = new AtomicLong[streamCount];
        this.columnCounters = new AtomicLong[streamCount];
        for (int i = 0; i < streamCount; i++) {
            this.nextTimestamps[i] = new AtomicLong(baseMicros);
            this.columnCounters[i] = new AtomicLong(0);
        }
    }

    public List<String> getSourceIds() {
        return Collections.unmodifiableList(sourceIds);
    }

    public Map<String, List<Integer>> getComplexBySource() {
        return Collections.unmodifiableMap(complexBySource);
    }

    public Map<String, List<Integer>> getRealBySource() {
        return Collections.unmodifiableMap(realBySource);
    }

    /**
     * Returns the current frame index (column counter) for the given stream.
     * Used by PmuWriteTest to determine downsample eligibility via modulo.
     */
    public long getFrameIndex(int streamIdx) {
        return columnCounters[streamIdx].get();
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public PmuFrame next() {
        // Round-robin stream selection
        int streamIdx = (int) (streamCounter.getAndIncrement() % streamCount);
        String streamId = streamPrefix + "_" + (streamStartIndex + streamIdx);

        // Advance timestamp for this stream
        long tsMicros = nextTimestamps[streamIdx].getAndAdd(intervalMicros);

        // Get column index (wraps around the 36001-column recording)
        int col = (int) (columnCounters[streamIdx].getAndIncrement() % numColumns);

        // Build source data from real CSV values
        List<PmuSourceData> sources = new ArrayList<>(sourceIds.size());

        for (String srcId : sourceIds) {
            // Build cx map from real CSV data
            Map<Value, Value> cxMap = new HashMap<>();
            List<Integer> cxIndices = complexBySource.get(srcId);
            if (cxIndices != null) {
                for (int globalIdx : cxIndices) {
                    double rv = cxReal[globalIdx][col];
                    double iv = cxImag[globalIdx][col];
                    int q = cxQuality[globalIdx][col];
                    cxMap.put(Value.get((long) globalIdx), Value.get(List.of(rv, iv, q)));
                }
            }

            // Build rl map from real CSV data
            Map<Value, Value> rlMap = new HashMap<>();
            List<Integer> rlIndices = realBySource.get(srcId);
            if (rlIndices != null) {
                for (int globalIdx : rlIndices) {
                    double v = rlValue[globalIdx][col];
                    int qr = rlQuality[globalIdx][col];
                    rlMap.put(Value.get((long) globalIdx), Value.get(List.of(v, qr)));
                }
            }

            sources.add(new PmuSourceData(srcId, cxMap, rlMap));
        }

        return new PmuFrame(streamId, tsMicros, sources, streamIdx);
    }

    // --- CSV/JSON parsing (same logic as Hitachi DataLoaderService) ---

    private double[][] readCsvMatrix(String resource, int expectedRows) throws IOException {
        double[][] matrix = new double[expectedRows][];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource),
                        resource + " not found on classpath")))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null && row < expectedRows) {
                String[] parts = line.split(",");
                matrix[row] = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    matrix[row][i] = Double.parseDouble(parts[i].trim());
                }
                row++;
            }
        }
        return matrix;
    }

    private int[][] readCsvIntMatrix(String resource, int expectedRows) throws IOException {
        int[][] matrix = new int[expectedRows][];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource),
                        resource + " not found on classpath")))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null && row < expectedRows) {
                String[] parts = line.split(",");
                matrix[row] = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    matrix[row][i] = (int) Double.parseDouble(parts[i].trim());
                }
                row++;
            }
        }
        return matrix;
    }

    private List<Integer> jsonArrayToIntList(JsonNode node) {
        List<Integer> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode elem : node) {
                list.add(elem.asInt());
            }
        }
        return list;
    }
}
