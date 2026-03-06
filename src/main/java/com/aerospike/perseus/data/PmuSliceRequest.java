package com.aerospike.perseus.data;

import java.util.List;

/**
 * A request for THE SLICE read: batch read all sources at one timestamp for one PDC stream.
 */
public class PmuSliceRequest {

    private final String streamId;
    private final long tsMicros;
    private final List<String> sourceIds;

    public PmuSliceRequest(String streamId, long tsMicros, List<String> sourceIds) {
        this.streamId = streamId;
        this.tsMicros = tsMicros;
        this.sourceIds = sourceIds;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getTsMicros() {
        return tsMicros;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }
}
