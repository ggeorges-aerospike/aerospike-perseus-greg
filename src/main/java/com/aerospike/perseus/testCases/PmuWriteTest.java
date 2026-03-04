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

    private final PmuTimestampTracker tracker;
    private final int ttlSeconds;

    public PmuWriteTest(TestCaseConstructorArguments arguments,
                        PmuFrameGenerator generator,
                        PmuTimestampTracker tracker,
                        int ttlSeconds) {
        super(arguments, generator);
        this.tracker = tracker;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    protected void execute(PmuFrame frame) {
        BatchWritePolicy bwPolicy = new BatchWritePolicy();
        if (ttlSeconds > 0) {
            bwPolicy.expiration = ttlSeconds;
        }

        List<BatchRecord> batchRecords = new ArrayList<>(frame.getDevices().size() + 1);

        for (PmuDeviceData dev : frame.getDevices()) {
            String keyStr = frame.getStreamId() + ":" + dev.getDeviceId() + ":" + frame.getTsMicros();
            Key key = new Key(namespace, PMU_SET, keyStr);

            List<Operation> ops = new ArrayList<>(2);
            if (dev.getCxMap() != null && !dev.getCxMap().isEmpty()) {
                ops.add(MapOperation.putItems(ORDERED_POLICY, CX_BIN, dev.getCxMap()));
            }
            if (dev.getRlMap() != null && !dev.getRlMap().isEmpty()) {
                ops.add(MapOperation.putItems(ORDERED_POLICY, RL_BIN, dev.getRlMap()));
            }

            if (!ops.isEmpty()) {
                batchRecords.add(new BatchWrite(bwPolicy, key, ops.toArray(new Operation[0])));
            }
        }

        // Write sentinel for latest timestamp tracking
        Key sentinelKey = new Key(namespace, META_SET, "latest:" + frame.getStreamId());
        Operation sentinelOp = Operation.put(new Bin("ts", frame.getTsMicros()));
        batchRecords.add(new BatchWrite(bwPolicy, sentinelKey, new Operation[]{sentinelOp}));

        if (!batchRecords.isEmpty()) {
            client.operate(client.batchPolicyDefault, batchRecords);
        }

        // Track for slice reads
        tracker.record(frame.getStreamId(), frame.getTsMicros());
    }

    @Override
    public String[] getHeader() {
        return "PMU Write\n6 dev/frame".split("\n");
    }
}
