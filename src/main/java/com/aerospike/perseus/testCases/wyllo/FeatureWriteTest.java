package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

/**
 * Upsert one entity's features onto a user record — the feature pipeline's unit of work.
 * Atomic on the record without a transaction: it is a single-record operate.
 */
public class FeatureWriteTest extends Test<FeatureGenerator.Write> {
    private final WritePolicy wp = new WritePolicy();

    public FeatureWriteTest(TestCaseConstructorArguments args, FeatureGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    @Override protected void execute(FeatureGenerator.Write w) {
        try {
            client.operate(wp, getKey(w.userId()), FeatureOps.upsert(w));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "feature\nwrite".split("\n"); }
}
