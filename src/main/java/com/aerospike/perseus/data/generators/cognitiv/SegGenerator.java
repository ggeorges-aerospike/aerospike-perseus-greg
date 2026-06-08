package com.aerospike.perseus.data.generators.cognitiv;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * tbl_segments: (identity, segment_type_id) -> epoch_ms, segments map<int,bigint>.
 * Aerospike model: one record per (identity, segment_type_id) with a composite
 * string key "identity:typeId", bins `epoch`(long) and `segs`(map<int,bigint>).
 * Kept as a composite key (not collapsed) because the value is itself a map.
 */
public class SegGenerator implements Iterator<SegGenerator.Write> {

    public record Write(long identity, int typeId, long epochMs, Map<Integer, Long> segments) {}

    private final long offset;
    private final int typeUniverse;
    private final int avgTypesPerIdentity;
    private final int innerSegmentsPerType;
    private final AtomicLong seq = new AtomicLong();

    public SegGenerator(int perseusId, int typeUniverse, int avgTypesPerIdentity, int innerSegmentsPerType) {
        this.offset = (perseusId + 1L) * 1_000_000_000_000L;
        this.typeUniverse = Math.max(typeUniverse, 1);
        this.avgTypesPerIdentity = Math.max(avgTypesPerIdentity, 1);
        this.innerSegmentsPerType = Math.max(innerSegmentsPerType, 1);
    }

    @Override public boolean hasNext() { return true; }

    @Override public Write next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long s = seq.getAndIncrement();
        long identity = offset + (s / avgTypesPerIdentity);
        int typeId = 1 + r.nextInt(typeUniverse);
        Map<Integer, Long> segments = new HashMap<>(innerSegmentsPerType * 2);
        for (int i = 0; i < innerSegmentsPerType; i++) segments.put(1 + r.nextInt(1_000_000), r.nextLong());
        return new Write(identity, typeId, System.currentTimeMillis(), segments);
    }

    public long offset() { return offset; }
    public int typeUniverse() { return typeUniverse; }
    public long partitionsWritten() { return Math.max(1, seq.get() / avgTypesPerIdentity); }
}
