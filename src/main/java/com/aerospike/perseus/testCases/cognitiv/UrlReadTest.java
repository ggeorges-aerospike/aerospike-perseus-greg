package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Partition read: all providers for a url — SELECT ... WHERE url_hash = ? (single record get). */
public class UrlReadTest extends Test<Long> {
    private final UrlGenerator gen;

    public UrlReadTest(TestCaseConstructorArguments args, UrlGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        long urlHash = gen.offset() + ThreadLocalRandom.current().nextLong(gen.partitionsWritten());
        try { client.get(null, getKey(urlHash)); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "url\npart read".split("\n"); }
}
