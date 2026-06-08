package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Partition read: all segment_type_ids for an identity — SELECT ... WHERE identity = ?.
 *  Composite-key model => batch-get over the type universe for one identity. */
public class SegReadTest extends Test<Long> {
    private final SegGenerator gen;

    public SegReadTest(TestCaseConstructorArguments args, SegGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        long identity = gen.offset() + ThreadLocalRandom.current().nextLong(gen.partitionsWritten());
        int types = gen.typeUniverse();
        Key[] keys = new Key[types];
        for (int t = 0; t < types; t++) keys[t] = SegWriteTest.key(namespace, setName, identity, t + 1);
        try { client.get(null, keys); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "seg\npart read".split("\n"); }
}
