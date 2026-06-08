package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** The `UPDATE ... USING TTL ? SET segments[?]=? WHERE identity=? AND segment_type_id=?` op:
 *  a single map-put on the `segs` bin that also refreshes the record TTL. */
public class SegUpdateTest extends Test<Long> {
    private final SegGenerator gen;
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);
    private final WritePolicy wp = new WritePolicy();

    public SegUpdateTest(TestCaseConstructorArguments args, SegGenerator gen, int ttlSeconds) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long identity = gen.offset() + r.nextLong(gen.partitionsWritten());
        int typeId = 1 + r.nextInt(gen.typeUniverse());
        try {
            client.operate(wp, SegWriteTest.key(namespace, setName, identity, typeId),
                    MapOperation.put(mapPolicy, SegWriteTest.SEGS, Value.get(1 + r.nextInt(1_000_000)), Value.get(r.nextLong())));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "seg\nmap update".split("\n"); }
}
