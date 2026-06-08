package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.concurrent.ThreadLocalRandom;

/** Point read: one provider of one url — WHERE url_hash=? AND provider_id=? (map getByKey). */
public class UrlPointReadTest extends Test<Long> {
    private final UrlGenerator gen;

    public UrlPointReadTest(TestCaseConstructorArguments args, UrlGenerator gen) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long urlHash = gen.offset() + r.nextLong(gen.partitionsWritten());
        int providerId = 1 + r.nextInt(gen.providerUniverse());
        try {
            client.operate(null, getKey(urlHash),
                    MapOperation.getByKey(UrlWriteTest.BIN, Value.get(providerId), MapReturnType.VALUE));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return "url\npoint read".split("\n"); }
}
