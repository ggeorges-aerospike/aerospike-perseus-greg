package com.aerospike.perseus.data.generators;

import com.aerospike.client.Value;
import com.aerospike.perseus.data.PmuDeviceData;
import com.aerospike.perseus.data.PmuFrame;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates PMU frames matching the Hitachi RTDB data model exactly.
 *
 * Data model per device record:
 *   Key:  "{streamId}:{deviceId}:{timestampMicros}"
 *   Bin "cx": KEY_ORDERED map { Long globalIndex → List[real, imag, quality] }
 *   Bin "rl": KEY_ORDERED map { Long globalIndex → List[value, quality] }
 *
 * 27-bus system: 6 devices, 137 complex phasors, 144 real analogs.
 * Timestamps advance at 200 FPS (5000 µs intervals).
 * Streams are round-robined across configured stream count.
 */
public class PmuFrameGenerator extends BaseGenerator<PmuFrame> {

    private final int streamCount;
    private final String streamPrefix;
    private final int deviceCount;
    private final int complexCount;
    private final int realCount;
    private final long intervalMicros;

    // Per-stream timestamp tracking
    private final AtomicLong[] nextTimestamps;
    private final AtomicLong streamCounter = new AtomicLong(0);

    // Device mapping: which global indices belong to which device
    private final List<String> deviceIds;
    private final Map<String, List<Integer>> complexByDevice;
    private final Map<String, List<Integer>> realByDevice;

    private final int streamStartIndex;

    public PmuFrameGenerator(int streamCount, String streamPrefix, int streamStartIndex,
                             int deviceCount, int complexCount, int realCount, int fps) {
        this.streamCount = streamCount;
        this.streamPrefix = streamPrefix;
        this.streamStartIndex = streamStartIndex;
        this.deviceCount = deviceCount;
        this.complexCount = complexCount;
        this.realCount = realCount;
        this.intervalMicros = 1_000_000L / fps;

        // Initialize per-stream timestamps from now
        long baseMicros = System.currentTimeMillis() * 1000;
        this.nextTimestamps = new AtomicLong[streamCount];
        for (int i = 0; i < streamCount; i++) {
            this.nextTimestamps[i] = new AtomicLong(baseMicros);
        }

        // Build device mapping: distribute measurements evenly across devices
        this.deviceIds = new ArrayList<>(deviceCount);
        this.complexByDevice = new LinkedHashMap<>();
        this.realByDevice = new LinkedHashMap<>();

        for (int d = 0; d < deviceCount; d++) {
            String devId = "PMU_" + (d + 1);
            deviceIds.add(devId);
            complexByDevice.put(devId, new ArrayList<>());
            realByDevice.put(devId, new ArrayList<>());
        }

        // Distribute complex measurements round-robin across devices
        for (int i = 0; i < complexCount; i++) {
            String devId = deviceIds.get(i % deviceCount);
            complexByDevice.get(devId).add(i);
        }

        // Distribute real measurements round-robin across devices
        for (int i = 0; i < realCount; i++) {
            String devId = deviceIds.get(i % deviceCount);
            realByDevice.get(devId).add(i);
        }
    }

    public List<String> getDeviceIds() {
        return Collections.unmodifiableList(deviceIds);
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

        // Generate device data
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double t = tsMicros / 1_000_000.0;  // seconds for sinusoidal generation
        List<PmuDeviceData> devices = new ArrayList<>(deviceCount);

        for (String devId : deviceIds) {
            // Build cx map: globalIdx → [real, imag, quality]
            Map<Value, Value> cxMap = new TreeMap<>();
            List<Integer> cxIndices = complexByDevice.get(devId);
            for (int idx : cxIndices) {
                double phase = 2 * Math.PI * 50 * t + idx * 0.1;
                double magnitude = 100.0 + idx * 2.0 + rnd.nextDouble() * 0.5;
                double real = magnitude * Math.cos(phase);
                double imag = magnitude * Math.sin(phase);
                int quality = 0;

                List<Value> vals = List.of(
                        Value.get(real),
                        Value.get(imag),
                        Value.get(quality)
                );
                cxMap.put(Value.get((long) idx), Value.get(vals));
            }

            // Build rl map: globalIdx → [value, quality]
            Map<Value, Value> rlMap = new TreeMap<>();
            List<Integer> rlIndices = realByDevice.get(devId);
            for (int idx : rlIndices) {
                double value;
                if (idx < 90) {
                    value = 1.0;  // status flags
                } else if (idx < 117) {
                    value = 49.5 + rnd.nextDouble() * 1.0;  // frequency ~50Hz
                } else {
                    value = -0.01 + rnd.nextDouble() * 0.02;  // ROCOF
                }
                int quality = 1;

                List<Value> vals = List.of(
                        Value.get(value),
                        Value.get(quality)
                );
                rlMap.put(Value.get((long) idx), Value.get(vals));
            }

            devices.add(new PmuDeviceData(devId, cxMap, rlMap));
        }

        return new PmuFrame(streamId, tsMicros, devices);
    }
}
