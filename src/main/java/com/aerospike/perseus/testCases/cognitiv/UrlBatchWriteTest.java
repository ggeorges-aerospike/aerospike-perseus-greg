package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.Operation;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Batched url-segment inserts (Unlogged batch of provider map-puts across distinct urls). */
public class UrlBatchWriteTest extends Test<Long> {
    private final UrlGenerator gen;
    private final int batchSize;
    private final BatchWritePolicy bwp = new BatchWritePolicy();
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);

    public UrlBatchWriteTest(TestCaseConstructorArguments args, UrlGenerator gen, int batchSize, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
        bwp.sendKey = true;
        if (ttlSeconds > 0) bwp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        List<BatchRecord> recs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            UrlGenerator.Write w = gen.next();
            Value value = Value.get(Arrays.asList(w.refreshAfter(), w.requestCount(), w.segments()));
            recs.add(new BatchWrite(bwp, getKey(w.urlHash()),
                    new Operation[]{ MapOperation.put(mapPolicy, UrlWriteTest.BIN, Value.get(w.providerId()), value) }));
        }
        try { client.operate(null, recs); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("url\nbatch wr %d", batchSize).split("\n"); }
}
