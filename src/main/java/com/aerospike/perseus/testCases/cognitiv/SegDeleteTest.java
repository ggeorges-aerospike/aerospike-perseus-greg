package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** DELETE WHERE identity=? AND segment_type_id=? -> delete the whole composite-key record
 *  (in the composite-key model each (identity,type) IS one record). */
public class SegDeleteTest extends Test<Long> {
    private final SegGenerator gen;
    private final WritePolicy wp = new WritePolicy();

    public SegDeleteTest(TestCaseConstructorArguments args, SegGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        wp.sendKey = true;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long identity = gen.offset() + r.nextLong(gen.partitionsWritten());
        int typeId = 1 + r.nextInt(gen.typeUniverse());
        try {
            client.delete(wp, SegWriteTest.key(namespace, setName, identity, typeId));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "seg\ndelete".split("\n"); }
}
