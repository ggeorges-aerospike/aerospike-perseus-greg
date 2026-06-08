package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.BidGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

/** corvus draco.bids INSERT: id -> request (protobuf blob, bimodal size). */
public class BidWriteTest extends Test<BidGenerator.Write> {
    static final String BIN = "req";
    private final WritePolicy wp = new WritePolicy();

    public BidWriteTest(TestCaseConstructorArguments args, BidGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    @Override protected void execute(BidGenerator.Write w) {
        try {
            client.put(wp, getKey(w.id()), new Bin(BIN, w.req()));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "bid\nwrite".split("\n"); }
}
