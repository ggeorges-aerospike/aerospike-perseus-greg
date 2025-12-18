package com.aerospike.perseus.data.generators;

import com.aerospike.client.Bin;
import com.aerospike.client.Value;
import com.aerospike.perseus.data.BlueCatRecord;
import com.aerospike.perseus.data.generators.key.KeyGenerator;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * BlueCat / BlueSky Networks DNS event generator.
 */
public class BlueCatRecordGenerator extends BaseGenerator<BlueCatRecord> {

    private final KeyGenerator keyGenerator;

    // -------------------------
    // Scenario state
    // -------------------------

    private static final class Scenario {
        UUID cid;
        String sid;
        String spid;
        String sa;
        long nextTs;
        int remaining;
    }

    private Scenario scenario;

    // -------------------------
    // Constants / config
    // -------------------------

    private static final String CLASS_NAME =
            "org.aerospike.blueskynetworks.dns.query.model.EdgeDnsQueryEventEntity";

    private static final List<String> DOMAIN_POOL = List.of(
            "youtube.com.",
            "apple.com.",
            "nos.nl.",
            "facebook.cm.",
            "celcom.com.my."
    );

    private static final Set<String> HIGH_BLOCK = Set.of(
            "apple.com.",
            "nos.nl."
    );

    private static final Set<String> LOW_BLOCK = Set.of(
            "youtube.com.",
            "facebook.cm.",
            "celcom.com.my."
    );

    private static final List<String> SOURCE_ADDRESSES = List.of(
            "10.32.0.90",
            "10.32.0.95",
            "10.16.0.100",
            "10.16.0.18"
    );

    private static final List<Map<String, Object>> POLICY_POOL = List.of(
            policy("aece2d6a5dde79a0", "Fake 1", "fake1.com."),
            policy("554a14fc7395a9d3", "Fake 2", "fake2.com.")
    );

    private final Random rnd = new SecureRandom();


    public BlueCatRecordGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }
    // -------------------------
    // Generator contract
    // -------------------------

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public BlueCatRecord next() {
        if (scenario == null || scenario.remaining <= 0) {
            startScenario();
        }

        Map<String, Object> event = buildEvent();

        Long key = keyGenerator.next();

        Map<?, ?> ctx = (Map<?, ?>) event.get("ctx");

        Bin[] bins = new Bin[] {
                // Canonical payload
                new Bin("event", Value.get(event)),

                // Index / query friendly projections
                new Bin("cid", Value.get(event.get("cid"))),
                new Bin("et",  Value.get(event.get("et"))),
                new Bin("pd",  Value.get(ctx.get("pd"))),
                new Bin("sid", Value.get(ctx.get("sid"))),
                new Bin("sa",  Value.get(event.get("sa"))),
                new Bin("t",   Value.get(event.get("t")))
        };

        return new BlueCatRecord(key, bins);
    }

    // -------------------------
    // Scenario logic
    // -------------------------

    private void startScenario() {
        scenario = new Scenario();
        scenario.cid = UUID.randomUUID();
        scenario.sid = uuid();
        scenario.spid = uuid();
        scenario.sa = pick(SOURCE_ADDRESSES);
        scenario.nextTs = Instant.now().toEpochMilli() + rnd.nextInt(1000);
        scenario.remaining = rand(3, 10);
    }

    // -------------------------
    // Event construction
    // -------------------------

    private Map<String, Object> buildEvent() {
        String domain = pick(DOMAIN_POOL);
        boolean block = decideBlock(domain);

        long reqT = scenario.nextTs;
        long respT = reqT + rand(1, 3);
        scenario.nextTs += rand(50, 500);
        scenario.remaining--;

        Map<String, Object> event = new LinkedHashMap<>();

        event.put("PK", generatePk(reqT));
        event.put("@_class", CLASS_NAME);
        event.put("cid", scenario.cid.toString());
        event.put("et", block ? "BLOCK" : "QUERY_RESPONSE");
        event.put("sa", scenario.sa);
        event.put("t", reqT);

        event.put("ctx", ctx(domain));
        event.put("id", id());
        event.put("ns", List.of(ns(block)));
        event.put("req", req(domain, reqT));
        event.put("resp", resp(domain, respT));

        if (block) {
            event.put("pol", pickPolicies());
        }

        return event;
    }

    // -------------------------
    // Sections
    // -------------------------

    private Map<String, Object> ctx(String domain) {
        return map(
                "fam", "INET",
                "mpid", hex(16),
                "pd", domain,
                "proto", "UDP",
                "rd", "test_redirect_domain.com.",
                "sid", scenario.sid,
                "spid", scenario.spid,
                "sport", rand(1024, 65535)
        );
    }

    private Map<String, Object> id() {
        return map("iid", hex(16), "uid", hex(16));
    }

    private Map<String, Object> ns(boolean block) {
        return map(
                "nm", "Default",
                "id", hex(16),
                "lat", rand(0, 2),
                "rcode", block ? 10 : rand(0, 2),
                "cy", block ? rand(3, 10) : rand(0, 2)
        );
    }

    private Map<String, Object> req(String domain, long t) {
        return map(
                "hdr", hdr(),
                "q", List.of(map("cl", 0, "dn", domain, "qt", 1)),
                "rcode", 0,
                "sz", 0,
                "t", t
        );
    }

    private Map<String, Object> resp(String domain, long t) {
        List<Map<String, Object>> ans = new ArrayList<>();
        for (int i = 0; i < rand(2, 4); i++) {
            ans.add(map(
                    "cl", 0,
                    "dn", domain,
                    "r", ipv4(),
                    "rt", 1,
                    "ttl", 0
            ));
        }

        return map(
                "hdr", hdr(),
                "ans", ans,
                "auth", List.of(map(
                        "cl", 0,
                        "dn", domain,
                        "r", "ns." + stripDot(domain) + ".",
                        "rt", 2,
                        "ttl", 0
                )),
                "rcode", 0,
                "sz", 0,
                "t", t
        );
    }

    private Map<String, Object> hdr() {
        return map(
                "aa", false, "ad", false, "an", 0, "ar", 0,
                "cd", false, "id", 0, "ns", 0, "op", 0,
                "qd", 0, "qr", 0, "ra", false, "rc", 0,
                "rd", false, "tc", false
        );
    }

    // -------------------------
    // Policies
    // -------------------------

    private List<Map<String, Object>> pickPolicies() {
        Collections.shuffle(POLICY_POOL, rnd);
        return POLICY_POOL.subList(0, 1 + rnd.nextInt(2));
    }

    private static Map<String, Object> policy(String id, String name, String domain) {
        return map(
                "c", 1,
                "id", id,
                "nm", name,
                "cr", List.of(
                        map("t", "TIME",
                                "d", List.of(map("dn", domain)))
                )
        );
    }

    // -------------------------
    // Utils
    // -------------------------

    private boolean decideBlock(String domain) {
        double p = 0.25;
        if (HIGH_BLOCK.contains(domain)) p += 0.35;
        if (LOW_BLOCK.contains(domain)) p -= 0.20;
        return rnd.nextDouble() < p;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private <T> T pick(List<T> l) {
        return l.get(rnd.nextInt(l.size()));
    }

    private int rand(int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }

    private String hex(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("0123456789abcdef".charAt(rnd.nextInt(16)));
        return sb.toString();
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }

    private String ipv4() {
        return rand(2, 240) + "." + rand(0, 255) + "." + rand(0, 255) + "." + rand(1, 254);
    }

    private String generatePk(long t) {
        return t + hex(24).toUpperCase(Locale.ROOT);
    }

    private String stripDot(String d) {
        return d.endsWith(".") ? d.substring(0, d.length() - 1) : d;
    }
}
