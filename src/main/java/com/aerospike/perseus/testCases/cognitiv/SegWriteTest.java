package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.SegGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

/** Insert (identity, segment_type_id, epoch_ms, segments map): one record per composite key. */
public class SegWriteTest extends Test<SegGenerator.Write> {
    static final String EPOCH = "epoch";
    static final String SEGS = "segs";
    private final WritePolicy wp = new WritePolicy();

    public SegWriteTest(TestCaseConstructorArguments args, SegGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    static Key key(String namespace, String set, long identity, int typeId) {
        return new Key(namespace, set, identity + ":" + typeId);
    }

    @Override protected void execute(SegGenerator.Write w) {
        try {
            client.put(wp, key(namespace, setName, w.identity(), w.typeId()),
                    new Bin(EPOCH, w.epochMs()), new Bin(SEGS, Value.get(w.segments())));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "seg\nwrite".split("\n"); }
}
