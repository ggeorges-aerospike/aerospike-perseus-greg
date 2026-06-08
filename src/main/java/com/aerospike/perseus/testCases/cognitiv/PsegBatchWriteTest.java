package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.Operation;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.List;

/** Batched person-segment inserts — mirrors production ExecuteBatchAsync (Unlogged
 *  batch of writeBatchSize map-puts across DISTINCT persons in one round-trip). */
public class PsegBatchWriteTest extends Test<Long> {
    private final PsegGenerator gen;
    private final int batchSize;
    private final BatchWritePolicy bwp = new BatchWritePolicy();
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);

    public PsegBatchWriteTest(TestCaseConstructorArguments args, PsegGenerator gen, int batchSize, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
        bwp.sendKey = true;
        if (ttlSeconds > 0) bwp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        long now = System.currentTimeMillis();
        List<BatchRecord> recs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            PsegGenerator.Write w = gen.next();
            recs.add(new BatchWrite(bwp, getKey(w.personId()),
                    new Operation[]{ MapOperation.put(mapPolicy, PsegWriteTest.BIN, Value.get(w.segId()), Value.get(now)) }));
        }
        try { client.operate(null, recs); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("pseg\nbatch wr %d", batchSize).split("\n"); }
}
