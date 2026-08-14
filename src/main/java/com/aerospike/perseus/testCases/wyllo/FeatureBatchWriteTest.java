package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.Operation;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.List;

/**
 * Batched feature upserts across distinct users in one round trip.
 *
 * <p>This is the path to the 1M writes/sec target: at batch 100 each op is 100 record
 * writes, so the client needs ~10k ops/sec rather than 1M. Counted TPS here is
 * batches, not records — multiply by the batch size for record throughput.
 */
public class FeatureBatchWriteTest extends Test<Long> {
    private final FeatureGenerator gen;
    private final int batchSize;
    private final BatchWritePolicy bwp = new BatchWritePolicy();

    public FeatureBatchWriteTest(TestCaseConstructorArguments args, FeatureGenerator gen,
                                 int batchSize, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
        bwp.sendKey = true;
        if (ttlSeconds > 0) bwp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        List<BatchRecord> recs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            FeatureGenerator.Write w = gen.next();
            recs.add(new BatchWrite(bwp, getKey(w.userId()), new Operation[]{ FeatureOps.upsert(w) }));
        }
        try { client.operate(null, recs); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("feature\nbatch wr %d", batchSize).split("\n"); }
}
