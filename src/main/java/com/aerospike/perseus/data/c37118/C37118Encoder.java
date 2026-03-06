package com.aerospike.perseus.data.c37118;

import com.aerospike.client.Value;
import com.aerospike.perseus.data.PmuFrame;
import com.aerospike.perseus.data.PmuSourceData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static com.aerospike.perseus.data.c37118.C37118Constants.*;

/**
 * Encodes PmuFrame data into IEEE C37.118 binary frames.
 *
 * Supports CFG-2 (configuration) and DATA frame encoding.
 * All values encoded as float32, phasors in rectangular form.
 */
public class C37118Encoder {

    private final List<String> sourceIds;
    private final Map<String, List<Integer>> complexBySource;
    private final Map<String, List<Integer>> realBySource;
    private final int fps;
    private final int pdcIdcode;

    // Pre-computed data frame size (fixed for a given PMU topology)
    private final int dataFrameSize;

    public C37118Encoder(List<String> sourceIds,
                         Map<String, List<Integer>> complexBySource,
                         Map<String, List<Integer>> realBySource,
                         int fps) {
        this(sourceIds, complexBySource, realBySource, fps, 1);
    }

    public C37118Encoder(List<String> sourceIds,
                         Map<String, List<Integer>> complexBySource,
                         Map<String, List<Integer>> realBySource,
                         int fps, int pdcIdcode) {
        this.sourceIds = sourceIds;
        this.complexBySource = complexBySource;
        this.realBySource = realBySource;
        this.fps = fps;
        this.pdcIdcode = pdcIdcode;

        // Compute fixed data frame size:
        // HEADER(14) + STAT(2) [PDC aggregate] + per-PMU data + CRC(2)
        int perPmuTotal = 0;
        for (String srcId : sourceIds) {
            int phasorCount = complexBySource.getOrDefault(srcId, List.of()).size();
            int analogCount = realBySource.getOrDefault(srcId, List.of()).size();
            // STAT(2) + phasors(8 each) + FREQ(4) + DFREQ(4) + analogs(4 each)
            perPmuTotal += STAT_SIZE + (phasorCount * PHASOR_FLOAT_SIZE) +
                           FREQ_SIZE + DFREQ_SIZE + (analogCount * ANALOG_FLOAT_SIZE);
        }
        this.dataFrameSize = HEADER_SIZE + STAT_SIZE + perPmuTotal + CRC_SIZE;
    }

    /**
     * Encode a CFG-2 configuration frame describing the PMU topology.
     * Sent once at the start of a TCP connection.
     */
    public byte[] encodeCfg2Frame(long tsMicros) {
        // Calculate CFG-2 frame size
        int size = HEADER_SIZE;
        size += CFG2_TIMEBASE_SIZE;  // TIME_BASE
        size += CFG2_NUM_PMU_SIZE;   // NUM_PMU

        for (String srcId : sourceIds) {
            int phasorCount = complexBySource.getOrDefault(srcId, List.of()).size();
            int analogCount = realBySource.getOrDefault(srcId, List.of()).size();
            int digitalCount = 0;

            size += CFG2_STATION_NAME_SIZE;  // STN (station name)
            size += CFG2_IDCODE_SIZE;        // IDCODE
            size += CFG2_FORMAT_SIZE;        // FORMAT
            size += CFG2_PHNMR_SIZE;         // PHNMR
            size += CFG2_ANNMR_SIZE;         // ANNMR
            size += CFG2_DGNMR_SIZE;         // DGNMR
            size += (phasorCount + analogCount + digitalCount) * CFG2_CHANNEL_NAME_SIZE;  // channel names
            size += phasorCount * CFG2_PHUNIT_SIZE;     // PHUNIT
            size += analogCount * CFG2_ANUNIT_SIZE;     // ANUNIT
            size += digitalCount * CFG2_DIGUNIT_SIZE;   // DIGUNIT
            size += CFG2_FNOM_SIZE;          // FNOM
            size += CFG2_CFGCNT_SIZE;        // CFGCNT
        }

        size += CFG2_DATA_RATE_SIZE;  // DATA_RATE
        size += CRC_SIZE;             // CRC

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(ByteOrder.BIG_ENDIAN);

        // Header
        writeHeader(buf, SYNC_CFG2, size, pdcIdcode, tsMicros);

        // TIME_BASE
        buf.putInt(TIME_BASE);

        // NUM_PMU
        buf.putShort((short) sourceIds.size());

        // Per-PMU config
        for (int pmuIdx = 0; pmuIdx < sourceIds.size(); pmuIdx++) {
            String srcId = sourceIds.get(pmuIdx);
            int phasorCount = complexBySource.getOrDefault(srcId, List.of()).size();
            int analogCount = realBySource.getOrDefault(srcId, List.of()).size();
            int digitalCount = 0;

            // STN — 16-byte station name, padded with spaces
            writeFixedString(buf, srcId, CFG2_STATION_NAME_SIZE);

            // IDCODE — extract numeric ID from "PMU_N"
            int idcode = extractIdcode(srcId);
            buf.putShort((short) idcode);

            // FORMAT — float, rectangular
            buf.putShort((short) FORMAT_FLOAT_RECT);

            // PHNMR, ANNMR, DGNMR
            buf.putShort((short) phasorCount);
            buf.putShort((short) analogCount);
            buf.putShort((short) digitalCount);

            // Channel names — 16 bytes each
            List<Integer> cxIndices = complexBySource.getOrDefault(srcId, List.of());
            for (int i = 0; i < phasorCount; i++) {
                writeFixedString(buf, "PH_" + cxIndices.get(i), CFG2_CHANNEL_NAME_SIZE);
            }
            List<Integer> rlIndices = realBySource.getOrDefault(srcId, List.of());
            for (int i = 0; i < analogCount; i++) {
                writeFixedString(buf, "AN_" + rlIndices.get(i), CFG2_CHANNEL_NAME_SIZE);
            }

            // PHUNIT — phasor conversion factors (0 for float format)
            for (int i = 0; i < phasorCount; i++) {
                buf.putInt(0);
            }

            // ANUNIT — analog conversion factors (0 for float format)
            for (int i = 0; i < analogCount; i++) {
                buf.putInt(0);
            }

            // FNOM — 0x0001 = 50 Hz nominal (bit 0 set)
            buf.putShort((short) 0x0001);

            // CFGCNT — config change count (0 = initial config)
            buf.putShort((short) 0);
        }

        // DATA_RATE
        buf.putShort((short) fps);

        // CRC
        int crc = C37118Crc.compute(buf.array(), 0, size - CRC_SIZE);
        buf.putShort((short) crc);

        return buf.array();
    }

    /**
     * Encode a DATA frame from a PmuFrame.
     * Called once per frame at 200 FPS.
     */
    public byte[] encodeDataFrame(PmuFrame frame) {
        ByteBuffer buf = ByteBuffer.allocate(dataFrameSize);
        buf.order(ByteOrder.BIG_ENDIAN);

        // Header
        writeHeader(buf, SYNC_DATA, dataFrameSize, pdcIdcode, frame.getTsMicros());

        // PDC aggregate STAT word
        buf.putShort((short) STAT_OK);

        // Per-PMU data
        for (PmuSourceData src : frame.getSources()) {
            String srcId = src.getSourceId();

            // STAT word for this PMU
            buf.putShort((short) STAT_OK);

            // Phasors: float32 real + float32 imag for each complex phasor
            List<Integer> cxIndices = complexBySource.getOrDefault(srcId, List.of());
            Map<Value, Value> cxMap = src.getCxMap();
            for (int globalIdx : cxIndices) {
                float real = 0f, imag = 0f;
                if (cxMap != null) {
                    Value val = cxMap.get(Value.get((long) globalIdx));
                    if (val != null) {
                        Object obj = val.getObject();
                        if (obj instanceof List) {
                            List<?> triple = (List<?>) obj;
                            real = ((Number) triple.get(0)).floatValue();
                            imag = ((Number) triple.get(1)).floatValue();
                        }
                    }
                }
                buf.putFloat(real);
                buf.putFloat(imag);
            }

            // FREQ — nominal 50 Hz as deviation (0.0)
            buf.putFloat(0.0f);

            // DFREQ — rate of frequency change (0.0)
            buf.putFloat(0.0f);

            // Analog channels: float32 for each real measurement
            List<Integer> rlIndices = realBySource.getOrDefault(srcId, List.of());
            Map<Value, Value> rlMap = src.getRlMap();
            for (int globalIdx : rlIndices) {
                float value = 0f;
                if (rlMap != null) {
                    Value val = rlMap.get(Value.get((long) globalIdx));
                    if (val != null) {
                        Object obj = val.getObject();
                        if (obj instanceof List) {
                            List<?> pair = (List<?>) obj;
                            value = ((Number) pair.get(0)).floatValue();
                        }
                    }
                }
                buf.putFloat(value);
            }
        }

        // CRC over everything except the last 2 bytes
        int crc = C37118Crc.compute(buf.array(), 0, dataFrameSize - CRC_SIZE);
        buf.putShort((short) crc);

        return buf.array();
    }

    /**
     * Write the 14-byte C37.118 frame header.
     */
    private void writeHeader(ByteBuffer buf, int syncWord, int frameSize, int idcode, long tsMicros) {
        buf.putShort((short) syncWord);
        buf.putShort((short) frameSize);
        buf.putShort((short) idcode);

        // SOC — seconds since Unix epoch
        int soc = (int) (tsMicros / 1_000_000L);
        buf.putInt(soc);

        // FRACSEC — fractional second (microseconds within the current second)
        // Bits [23:0] = fraction of second, bits [31:24] = time quality (0 = locked)
        int fracSec = (int) (tsMicros % 1_000_000L);
        buf.putInt(fracSec);
    }

    private void writeFixedString(ByteBuffer buf, String str, int size) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, size);
        buf.put(bytes, 0, len);
        // Pad with spaces
        for (int i = len; i < size; i++) {
            buf.put((byte) ' ');
        }
    }

    private int extractIdcode(String sourceId) {
        // Extract numeric part from "PMU_N"
        try {
            return Integer.parseInt(sourceId.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
