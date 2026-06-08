package com.aerospike.perseus.data.generators.cognitiv;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * tbl_person_identity_segments: (person_id, user_segment_id) -> inserted_at.
 * Aerospike model: one record per person_id, bin `segs` = map{segId -> insertedAt}.
 * Each next() is one segment insert for the "current" person; the person advances
 * every ~avgSegmentsPerPerson inserts, so person cardinality grows with load.
 */
public class PsegGenerator implements Iterator<PsegGenerator.Write> {

    public record Write(long personId, int segId) {}

    private final long offset;
    private final int avgSegmentsPerPerson;
    private final int segmentUniverse;
    private final AtomicLong seq = new AtomicLong();

    public PsegGenerator(int perseusId, int avgSegmentsPerPerson, int segmentUniverse) {
        this.offset = (perseusId + 1L) * 1_000_000_000_000L;   // per-instance key range
        this.avgSegmentsPerPerson = Math.max(avgSegmentsPerPerson, 1);
        this.segmentUniverse = Math.max(segmentUniverse, 1);
    }

    @Override public boolean hasNext() { return true; }

    @Override public Write next() {
        // Spread writes RANDOMLY across the persons created so far (no single hot
        // "current" person record) — matches production, where writes fan out
        // across many persons, and lets batches touch distinct keys.
        long persons = Math.max(1, seq.getAndIncrement() / avgSegmentsPerPerson);
        long personId = offset + ThreadLocalRandom.current().nextLong(persons);
        int segId = 1 + ThreadLocalRandom.current().nextInt(segmentUniverse);
        return new Write(personId, segId);
    }

    public long offset() { return offset; }
    public int segmentUniverse() { return segmentUniverse; }
    /** Distinct person_ids written so far (read tests pick within this range). */
    public long partitionsWritten() { return Math.max(1, seq.get() / avgSegmentsPerPerson); }
}
