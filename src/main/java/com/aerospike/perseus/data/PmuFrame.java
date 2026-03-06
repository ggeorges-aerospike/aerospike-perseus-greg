package com.aerospike.perseus.data;

import java.util.List;

/**
 * A single PMU frame: one timestamp across all sources for one PDC stream.
 * Each frame produces N source records (one per PMU source IDCODE).
 */
public class PmuFrame {

    private final String streamId;
    private final long tsMicros;
    private final List<PmuSourceData> sources;
    private final int localStreamIndex;

    public PmuFrame(String streamId, long tsMicros, List<PmuSourceData> sources) {
        this(streamId, tsMicros, sources, 0);
    }

    public PmuFrame(String streamId, long tsMicros, List<PmuSourceData> sources, int localStreamIndex) {
        this.streamId = streamId;
        this.tsMicros = tsMicros;
        this.sources = sources;
        this.localStreamIndex = localStreamIndex;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getTsMicros() {
        return tsMicros;
    }

    public List<PmuSourceData> getSources() {
        return sources;
    }

    public int getLocalStreamIndex() {
        return localStreamIndex;
    }
}
