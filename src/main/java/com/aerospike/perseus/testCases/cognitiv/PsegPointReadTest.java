package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Point read: one segment of one person — SELECT ... WHERE person_id=? AND user_segment_id=? (map getByKey). */
public class PsegPointReadTest extends Test<Long> {
    private final PsegGenerator gen;

    public PsegPointReadTest(TestCaseConstructorArguments args, PsegGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long personId = gen.offset() + r.nextLong(gen.partitionsWritten());
        int segId = 1 + r.nextInt(gen.segmentUniverse());
        try {
            client.operate(null, getKey(personId),
                    MapOperation.getByKey(PsegWriteTest.BIN, Value.get(segId), MapReturnType.VALUE));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "pseg\npoint read".split("\n"); }
}
