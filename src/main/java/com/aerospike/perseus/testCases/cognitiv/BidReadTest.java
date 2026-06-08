package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.perseus.data.generators.cognitiv.BidGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** corvus bid retrieve: SELECT * FROM bids WHERE id = ? — single-record get of the blob. */
public class BidReadTest extends Test<Long> {
    private final BidGenerator gen;

    public BidReadTest(TestCaseConstructorArguments args, BidGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        long id = gen.offset() + ThreadLocalRandom.current().nextLong(gen.partitionsWritten());
        try { client.get(null, getKey(id)); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "bid\nread".split("\n"); }
}
