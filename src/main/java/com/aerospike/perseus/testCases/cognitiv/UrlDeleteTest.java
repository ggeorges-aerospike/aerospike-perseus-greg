package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** DELETE WHERE url_hash=? AND provider_id=? -> remove one entry from the url's prov map.
 *  expiration=-2 so the record's TTL is preserved. */
public class UrlDeleteTest extends Test<Long> {
    private final UrlGenerator gen;
    private final WritePolicy wp = new WritePolicy();

    public UrlDeleteTest(TestCaseConstructorArguments args, UrlGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        wp.sendKey = true;
        wp.expiration = -2;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long urlHash = gen.offset() + r.nextLong(gen.partitionsWritten());
        int providerId = 1 + r.nextInt(gen.providerUniverse());
        try {
            client.operate(wp, getKey(urlHash),
                    MapOperation.removeByKey(UrlWriteTest.BIN, Value.get(providerId), MapReturnType.NONE));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "url\ndelete".split("\n"); }
}
