package com.aerospike.perseus.data.generators.cognitiv;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * draco.bids: id -> request (protobuf blob). Pure KV. Blob size is BIMODAL:
 * largeRatio of writes are ~largeBytes (default 30 KB), the rest ~smallBytes
 * (default 800 B), each with +/-10% Gaussian jitter — modelled exactly rather
 * than as a flat average, since the 30 KB blobs dominate storage and latency.
 */
public class BidGenerator implements Iterator<BidGenerator.Write> {

    public record Write(long id, byte[] req) {}

    private final long offset;
    private final int smallBytes;
    private final int largeBytes;
    private final double largeRatio;
    private final AtomicLong seq = new AtomicLong();

    public BidGenerator(int perseusId, int smallBytes, int largeBytes, double largeRatio) {
        this.offset = (perseusId + 1L) * 1_000_000_000_000L;
        this.smallBytes = Math.max(smallBytes, 1);
        this.largeBytes = Math.max(largeBytes, 1);
        this.largeRatio = Math.min(Math.max(largeRatio, 0.0), 1.0);
    }

    @Override public boolean hasNext() { return true; }

    @Override public Write next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long id = offset + seq.getAndIncrement();
        int mean = (r.nextDouble() < largeRatio) ? largeBytes : smallBytes;
        int size = (int) Math.max(1, r.nextGaussian(mean, mean / 10.0));
        byte[] req = new byte[size];
        r.nextBytes(req);
        return new Write(id, req);
    }

    public long offset() { return offset; }
    public long partitionsWritten() { return Math.max(1, seq.get()); }
}
