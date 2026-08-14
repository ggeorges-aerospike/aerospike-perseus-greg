package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One entity type for one user — {@code get(key, "bin_ip")}.
 *
 * <p>This is the read the bin-per-entity-type model exists to make cheap: naming the bin
 * returns only that slice, with no expression and nothing filtered client-side. Compare its
 * latency against {@link FeatureReadTest} to size the benefit.
 */
public class FeatureBinReadTest extends Test<Long> {
    private final FeatureGenerator gen;

    public FeatureBinReadTest(TestCaseConstructorArguments args, FeatureGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String userId = gen.randomWrittenUser(r);
        String bin = gen.pickSpec(r).bin();
        try { client.get(null, getKey(userId), bin); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "feature\nbin read".split("\n"); }
}
