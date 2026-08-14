package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Batch read of N distinct user records in one round trip — scoring a page of users at once.
 * TPS here counts batches, not records.
 */
public class FeatureBatchReadTest extends Test<Long> {
    private final FeatureGenerator gen;
    private final int batchSize;

    public FeatureBatchReadTest(TestCaseConstructorArguments args, FeatureGenerator gen, int batchSize) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Key[] keys = new Key[batchSize];
        for (int i = 0; i < batchSize; i++) keys[i] = getKey(gen.randomWrittenUser(r));
        try { client.get(null, keys); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("feature\nbatch rd %d", batchSize).split("\n"); }
}
