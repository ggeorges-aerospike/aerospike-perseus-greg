package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Batch partition read: SELECT ... WHERE person_id IN ? — batch-get of N person records. */
public class PsegBatchReadTest extends Test<Long> {
    private final PsegGenerator gen;
    private final int batchSize;

    public PsegBatchReadTest(TestCaseConstructorArguments args, PsegGenerator gen, int batchSize) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long written = gen.partitionsWritten();
        Key[] keys = new Key[batchSize];
        for (int i = 0; i < batchSize; i++) keys[i] = getKey(gen.offset() + r.nextLong(written));
        try { client.get(null, keys); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("pseg\nbatch %d", batchSize).split("\n"); }
}
