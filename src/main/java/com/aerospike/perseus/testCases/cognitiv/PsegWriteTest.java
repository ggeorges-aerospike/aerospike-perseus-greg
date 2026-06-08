package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.PsegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

/** Insert one (person_id, user_segment_id): map-put {segId -> now} into the person's `segs` map. */
public class PsegWriteTest extends Test<PsegGenerator.Write> {
    static final String BIN = "segs";
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.UNORDERED, MapWriteMode.UPDATE);
    private final WritePolicy wp = new WritePolicy();

    public PsegWriteTest(TestCaseConstructorArguments args, PsegGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    @Override protected void execute(PsegGenerator.Write w) {
        try {
            client.operate(wp, getKey(w.personId()),
                    MapOperation.put(mapPolicy, BIN, Value.get(w.segId()), Value.get(System.currentTimeMillis())));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "pseg\nwrite".split("\n"); }
}
