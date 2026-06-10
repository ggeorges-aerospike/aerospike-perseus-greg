package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** DELETE WHERE person_id=? AND user_segment_id=? -> remove one entry from the person's segs map.
 *  expiration=-2 (TTL_DONT_UPDATE) so removing a segment doesn't reset the record's TTL. */
public class PsegDeleteTest extends Test<Long> {
    private final PsegGenerator gen;
    private final WritePolicy wp = new WritePolicy();

    public PsegDeleteTest(TestCaseConstructorArguments args, PsegGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        wp.sendKey = true;
        wp.expiration = -2;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long personId = gen.offset() + r.nextLong(gen.partitionsWritten());
        int segId = 1 + r.nextInt(gen.segmentUniverse());
        try {
            client.operate(wp, getKey(personId),
                    MapOperation.removeByKey(PsegWriteTest.BIN, Value.get(segId), MapReturnType.NONE));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "pseg\ndelete".split("\n"); }
}
