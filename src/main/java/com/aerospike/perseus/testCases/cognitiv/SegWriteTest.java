package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.HashMap;
import java.util.Map;

/** Insert (identity, segment_type_id, epoch_ms, segments map): one record per composite key.
 *  The `segs` map is created KEY_ORDERED (cookbook best practice) atomically with `epoch`. */
public class SegWriteTest extends Test<SegGenerator.Write> {
    static final String EPOCH = "epoch";
    static final String SEGS = "segs";
    private final WritePolicy wp = new WritePolicy();
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);

    public SegWriteTest(TestCaseConstructorArguments args, SegGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    static Key key(String namespace, String set, long identity, int typeId) {
        return new Key(namespace, set, identity + ":" + typeId);
    }

    @Override protected void execute(SegGenerator.Write w) {
        Map<Value, Value> segs = new HashMap<>(w.segments().size() * 2);
        w.segments().forEach((k, v) -> segs.put(Value.get(k), Value.get(v)));
        try {
            client.operate(wp, key(namespace, setName, w.identity(), w.typeId()),
                    Operation.put(new Bin(EPOCH, w.epochMs())),
                    MapOperation.putItems(mapPolicy, SEGS, segs));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "seg\nwrite".split("\n"); }
}
