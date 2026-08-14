package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.data.generators.wyllo.FeatureIds;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * All features of ONE entity instance — e.g. every feature for one card.
 * {@code getByKey} on the outer map returns the whole inner map, server-side.
 */
public class FeatureEntityReadTest extends Test<Long> {
    private final FeatureGenerator gen;

    public FeatureEntityReadTest(TestCaseConstructorArguments args, FeatureGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String userId = gen.randomWrittenUser(r);
        FeatureGenerator.EntitySpec spec = gen.pickSpec(r);
        int instance = r.nextInt(Math.max(spec.maxEntitiesPerUser(), 1));
        String entityId = FeatureIds.entityId(spec.bin(), userId, instance, gen.binUniverse());
        try {
            client.operate(null, getKey(userId),
                    MapOperation.getByKey(spec.bin(), Value.get(entityId), MapReturnType.VALUE));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "feature\nentity rd".split("\n"); }
}
