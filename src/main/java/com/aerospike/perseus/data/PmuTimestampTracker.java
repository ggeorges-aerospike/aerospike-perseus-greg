package com.aerospike.perseus.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread-safe tracker of recently-written (streamId, tsMicros) pairs.
 * Writers add entries; readers pick random entries for THE SLICE queries.
 * Uses a ring buffer to bound memory.
 */
public class PmuTimestampTracker {

    private static final int MAX_ENTRIES = 100_000;

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final List<String> deviceIds;

    public PmuTimestampTracker(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public void record(String streamId, long tsMicros) {
        entries.add(new Entry(streamId, tsMicros));
        // Trim oldest entries when buffer is full
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public PmuSliceRequest randomSliceRequest() {
        if (entries.isEmpty()) {
            return null;
        }
        Entry e = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        return new PmuSliceRequest(e.streamId, e.tsMicros, deviceIds);
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    private static class Entry {
        final String streamId;
        final long tsMicros;

        Entry(String streamId, long tsMicros) {
            this.streamId = streamId;
            this.tsMicros = tsMicros;
        }
    }
}
