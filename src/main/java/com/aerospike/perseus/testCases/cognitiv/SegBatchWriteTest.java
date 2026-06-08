package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRecord;
import com.aerospike.client.BatchWrite;
import com.aerospike.client.Bin;
import com.aerospike.client.Operation;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.BatchWritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Batched segment inserts (Unlogged batch of composite-key records across distinct identities). */
public class SegBatchWriteTest extends Test<Long> {
    private final SegGenerator gen;
    private final int batchSize;
    private final BatchWritePolicy bwp = new BatchWritePolicy();
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);

    public SegBatchWriteTest(TestCaseConstructorArguments args, SegGenerator gen, int batchSize, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.batchSize = Math.max(batchSize, 1);
        bwp.sendKey = true;
        if (ttlSeconds > 0) bwp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        List<BatchRecord> recs = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            SegGenerator.Write w = gen.next();
            Map<Value, Value> segs = new HashMap<>(w.segments().size() * 2);
            w.segments().forEach((k, v) -> segs.put(Value.get(k), Value.get(v)));
            recs.add(new BatchWrite(bwp, SegWriteTest.key(namespace, setName, w.identity(), w.typeId()),
                    new Operation[]{ Operation.put(new Bin(SegWriteTest.EPOCH, w.epochMs())),
                            MapOperation.putItems(mapPolicy, SegWriteTest.SEGS, segs) }));
        }
        try { client.operate(null, recs); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("seg\nbatch wr %d", batchSize).split("\n"); }
}
