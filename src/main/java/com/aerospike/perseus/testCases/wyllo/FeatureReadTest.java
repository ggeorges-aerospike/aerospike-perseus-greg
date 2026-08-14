package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Whole-user read: every entity type and every feature in one record get. */
public class FeatureReadTest extends Test<Long> {
    private final FeatureGenerator gen;

    public FeatureReadTest(TestCaseConstructorArguments args, FeatureGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        String userId = gen.randomWrittenUser(ThreadLocalRandom.current());
        try { client.get(null, getKey(userId)); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "feature\nuser read".split("\n"); }
}
