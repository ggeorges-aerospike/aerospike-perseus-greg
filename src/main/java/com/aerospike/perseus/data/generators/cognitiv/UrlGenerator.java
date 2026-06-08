package com.aerospike.perseus.data.generators.cognitiv;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * tbl_url_segments: (url_hash, provider_id) -> segments list<int>, refresh_after,
 * request_count. Aerospike model: one record per url_hash, bin `prov` =
 * map{provider_id -> [refresh_after, request_count, segments[]]}.
 */
public class UrlGenerator implements Iterator<UrlGenerator.Write> {

    public record Write(long urlHash, int providerId, long refreshAfter, int requestCount, List<Integer> segments) {}

    private final long offset;
    private final int avgProvidersPerUrl;
    private final int providerUniverse;
    private final int avgSegmentsPerUrl;
    private final int segmentUniverse;
    private final AtomicLong seq = new AtomicLong();

    public UrlGenerator(int perseusId, int avgProvidersPerUrl, int providerUniverse,
                        int avgSegmentsPerUrl, int segmentUniverse) {
        this.offset = (perseusId + 1L) * 1_000_000_000_000L;
        this.avgProvidersPerUrl = Math.max(avgProvidersPerUrl, 1);
        this.providerUniverse = Math.max(providerUniverse, 1);
        this.avgSegmentsPerUrl = Math.max(avgSegmentsPerUrl, 1);
        this.segmentUniverse = Math.max(segmentUniverse, 1);
    }

    @Override public boolean hasNext() { return true; }

    @Override public Write next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long s = seq.getAndIncrement();
        long urlHash = offset + (s / avgProvidersPerUrl);
        int providerId = 1 + r.nextInt(providerUniverse);
        List<Integer> segments = new ArrayList<>(avgSegmentsPerUrl);
        for (int i = 0; i < avgSegmentsPerUrl; i++) segments.add(1 + r.nextInt(segmentUniverse));
        return new Write(urlHash, providerId, System.currentTimeMillis() + 3_600_000L, r.nextInt(1000), segments);
    }

    public long offset() { return offset; }
    public int providerUniverse() { return providerUniverse; }
    public long partitionsWritten() { return Math.max(1, seq.get() / avgProvidersPerUrl); }
}
