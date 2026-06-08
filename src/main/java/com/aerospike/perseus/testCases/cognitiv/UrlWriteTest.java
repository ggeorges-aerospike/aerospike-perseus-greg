package com.aerospike.perseus.testCases.cognitiv;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.generators.cognitiv.UrlGenerator;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.Arrays;

/** Insert (url_hash, provider_id): map-put {provider -> [refresh_after, request_count, segments[]]} into `prov`. */
public class UrlWriteTest extends Test<UrlGenerator.Write> {
    static final String BIN = "prov";
    private final MapPolicy mapPolicy = new MapPolicy(MapOrder.UNORDERED, MapWriteMode.UPDATE);
    private final WritePolicy wp = new WritePolicy();

    public UrlWriteTest(TestCaseConstructorArguments args, UrlGenerator gen, int ttlSeconds) {
        super(args, gen);
        wp.sendKey = true;
        if (ttlSeconds > 0) wp.expiration = ttlSeconds;
    }

    @Override protected void execute(UrlGenerator.Write w) {
        Value value = Value.get(Arrays.asList(w.refreshAfter(), w.requestCount(), w.segments()));
        try {
            client.operate(wp, getKey(w.urlHash()),
                    MapOperation.put(mapPolicy, BIN, Value.get(w.providerId()), value));
        } catch (AerospikeException ignored) {}
    }

    public String[] getHeader() { return "url\nwrite".split("\n"); }
}
