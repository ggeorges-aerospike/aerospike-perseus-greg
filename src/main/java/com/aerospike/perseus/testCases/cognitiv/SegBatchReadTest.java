package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** segments PartitionsSelect: SELECT ... WHERE identity IN ? — all rows for N identities.
 *  Composite-key model => batch-get over (each identity x the type universe). */
public class SegBatchReadTest extends Test<Long> {
    private final SegGenerator gen;
    private final int identities;

    public SegBatchReadTest(TestCaseConstructorArguments args, SegGenerator gen, int identitiesInList) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        this.identities = Math.max(identitiesInList, 1);
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long written = gen.partitionsWritten();
        int types = gen.typeUniverse();
        List<Key> keys = new ArrayList<>(identities * types);
        for (int n = 0; n < identities; n++) {
            long identity = gen.offset() + r.nextLong(written);
            for (int t = 1; t <= types; t++) keys.add(SegWriteTest.key(namespace, setName, identity, t));
        }
        try { client.get(null, keys.toArray(new Key[0])); } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("seg\nbatch %d", identities).split("\n"); }
}
