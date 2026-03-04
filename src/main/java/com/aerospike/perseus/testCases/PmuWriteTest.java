package com.aerospike.perseus.testCases;

import com.aerospike.client.*;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteFlags;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.PmuDeviceData;
import com.aerospike.perseus.data.PmuFrame;
import com.aerospike.perseus.data.PmuTimestampTracker;
import com.aerospike.perseus.data.generators.PmuFrameGenerator;

import java.util.*;

/**
 * PMU write test: writes one frame (all devices) per execute() call.
 * Uses batch write with MapOperation.putItems to match the Hitachi data model exactly.
 *
 * Record layout per device:
 *   Key:  "{streamId}:{deviceId}:{timestampMicros}" (string key)
 *   Bin "cx":  KEY_ORDERED map { globalIndex (Long) → [real, imag, quality] }
 *   Bin "rl":  KEY_ORDERED map { globalIndex (Long) → [value, quality] }
 *   Set: "pmu_frames_rt"
 *
 * Also writes a sentinel record for latest timestamp tracking:
 *   Set: "pmu_meta", Key: "latest:{streamId}"
 *   Bin "ts": timestampMicros
 */
public class PmuWriteTest extends Test<PmuFrame> {

    private static final String PMU_SET = "pmu_frames_rt";
    private static final String META_SET = "pmu_meta";
    private static final String CX_BIN = "cx";
    private static final String RL_BIN = "rl";
    private static final MapPolicy ORDERED_POLICY =
            new MapPolicy(MapOrder.KEY_ORDERED, MapWriteFlags.DEFAULT);

    /**
     * Pre-computed downsample tiers. Each tier writes every Nth frame to a separate set.
     * 200 FPS / factor 20 = 10 FPS, 200 FPS / factor 4 = 50 FPS.
     */
    private static final int[][] DOWNSAMPLE_TIERS = {
            {20, 43200},   // factor 20 → 10 FPS set, TTL 12h (same as RT)
            {4, 43200},    // factor 4  → 50 FPS set, TTL 12h
    };
    private static final String[] DOWNSAMPLE_SETS = {
            "pmu_frames_10fps",
            "pmu_frames_50fps",
    };

    private final PmuFrameGenerator pmuGenerator;
    private final PmuTimestampTracker tracker;
    private final int ttlSeconds;

    public PmuWriteTest(TestCaseConstructorArguments arguments,
                        PmuFrameGenerator generator,
                        PmuTimestampTracker tracker,
                        int ttlSeconds) {
        super(arguments, generator);
        this.pmuGenerator = generator;
        this.tracker = tracker;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    protected void execute(PmuFrame frame) {
        BatchWritePolicy bwPolicy = new BatchWritePolicy();
        if (ttlSeconds > 0) {
            bwPolicy.expiration = ttlSeconds;
        }

        // Estimate batch size: devices + sentinel + potential downsample copies
        int estimatedSize = frame.getDevices().size() + 1 +
                (DOWNSAMPLE_TIERS.length * (frame.getDevices().size() + 1));
        List<BatchRecord> batchRecords = new ArrayList<>(estimatedSize);

        // Build device operations once — reused for RT and downsample sets
        List<Operation[]> deviceOps = new ArrayList<>(frame.getDevices().size());
        for (PmuDeviceData dev : frame.getDevices()) {
            List<Operation> ops = new ArrayList<>(2);
            if (dev.getCxMap() != null && !dev.getCxMap().isEmpty()) {
                ops.add(MapOperation.putItems(ORDERED_POLICY, CX_BIN, dev.getCxMap()));
            }
            if (dev.getRlMap() != null && !dev.getRlMap().isEmpty()) {
                ops.add(MapOperation.putItems(ORDERED_POLICY, RL_BIN, dev.getRlMap()));
            }
            deviceOps.add(ops.isEmpty() ? null : ops.toArray(new Operation[0]));
        }

        // Write to primary RT set
        addDeviceRecords(batchRecords, bwPolicy, PMU_SET, frame, deviceOps);
        addSentinel(batchRecords, bwPolicy, META_SET, frame);

        // Write to downsample sets if this frame lands on the downsample interval.
        // frameIndex is the column counter *after* increment, so subtract 1 for modulo.
        long frameIndex = pmuGenerator.getFrameIndex(getStreamIndex(frame)) - 1;
        for (int tier = 0; tier < DOWNSAMPLE_TIERS.length; tier++) {
            int factor = DOWNSAMPLE_TIERS[tier][0];
            if (frameIndex % factor == 0) {
                BatchWritePolicy dsBwPolicy = new BatchWritePolicy();
                int dsTtl = DOWNSAMPLE_TIERS[tier][1];
                if (dsTtl > 0) {
                    dsBwPolicy.expiration = dsTtl;
                }
                addDeviceRecords(batchRecords, dsBwPolicy, DOWNSAMPLE_SETS[tier], frame, deviceOps);
                addSentinel(batchRecords, dsBwPolicy, META_SET, frame);
            }
        }

        if (!batchRecords.isEmpty()) {
            client.operate(client.batchPolicyDefault, batchRecords);
        }

        // Track for slice reads
        tracker.record(frame.getStreamId(), frame.getTsMicros());
    }

    private void addDeviceRecords(List<BatchRecord> batch, BatchWritePolicy bwPolicy,
                                   String set, PmuFrame frame, List<Operation[]> deviceOps) {
        List<PmuDeviceData> devices = frame.getDevices();
        for (int i = 0; i < devices.size(); i++) {
            Operation[] ops = deviceOps.get(i);
            if (ops != null) {
                String keyStr = frame.getStreamId() + ":" + devices.get(i).getDeviceId() + ":" + frame.getTsMicros();
                Key key = new Key(namespace, set, keyStr);
                batch.add(new BatchWrite(bwPolicy, key, ops));
            }
        }
    }

    private void addSentinel(List<BatchRecord> batch, BatchWritePolicy bwPolicy,
                              String metaSet, PmuFrame frame) {
        Key sentinelKey = new Key(namespace, metaSet, "latest:" + frame.getStreamId());
        Operation sentinelOp = Operation.put(new Bin("ts", frame.getTsMicros()));
        batch.add(new BatchWrite(bwPolicy, sentinelKey, new Operation[]{sentinelOp}));
    }

    /**
     * Extract stream index from streamId (e.g., "stream_1" → 0, "stream_5" → 4).
     * Falls back to 0 if pattern doesn't match.
     */
    private int getStreamIndex(PmuFrame frame) {
        String sid = frame.getStreamId();
        int lastUnderscore = sid.lastIndexOf('_');
        if (lastUnderscore >= 0 && lastUnderscore < sid.length() - 1) {
            try {
                return Integer.parseInt(sid.substring(lastUnderscore + 1)) - 1;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public String[] getHeader() {
        return "PMU Write\n6 dev/frame".split("\n");
    }
}
