package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Partition read: all segments for a person — SELECT ... WHERE person_id = ? (single record get). */
public class PsegReadTest extends Test<Long> {
    private final PsegGenerator gen;

    public PsegReadTest(TestCaseConstructorArguments args, PsegGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        long personId = gen.offset() + ThreadLocalRandom.current().nextLong(gen.partitionsWritten());
        try { client.get(null, getKey(personId)); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "pseg\npart read".split("\n"); }
}
