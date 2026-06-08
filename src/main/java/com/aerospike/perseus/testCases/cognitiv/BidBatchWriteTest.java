package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.Bin;
import com.aerospike.client.Operation;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.BidGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.List;

/** Batched bid inserts — corvus's write path (Unlogged batch of blob puts in one round-trip). */
public class BidBatchWriteTest extends Test<Long> {
    private final BidGenerator gen;
    private final int batchSize;
    private final BatchWritePolicy bwp = new BatchWritePolicy();

    public BidBatchWriteTest(TestCaseConstructorArguments args, BidGenerator gen, int batchSize, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
        bwp.sendKey = true;
        if (ttlSeconds > 0) bwp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        List<BatchRecord> recs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            BidGenerator.Write w = gen.next();
            recs.add(new BatchWrite(bwp, getKey(w.id()),
                    new Operation[]{ Operation.put(new Bin(BidWriteTest.BIN, w.req())) }));
        }
        try { client.operate(null, recs); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("bid\nbatch wr %d", batchSize).split("\n"); }
}
