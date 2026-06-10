package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** url_segments PartitionsSelect: SELECT ... WHERE url_hash IN ? — batch-get of N url records. */
public class UrlBatchReadTest extends Test<Long> {
    private final UrlGenerator gen;
    private final int batchSize;

    public UrlBatchReadTest(TestCaseConstructorArguments args, UrlGenerator gen, int batchSize) {
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

    public String[] getHeader() { return String.format("url\nbatch %d", batchSize).split("\n"); }
}
