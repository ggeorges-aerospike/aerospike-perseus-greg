package com.aerospike.perseus.data;

import java.util.List;

/**
 * A request for THE SLICE read: batch read all devices at one timestamp for one stream.
 */
public class PmuSliceRequest {

    private final String streamId;
    private final long tsMicros;
    private final List<String> deviceIds;

    public PmuSliceRequest(String streamId, long tsMicros, List<String> deviceIds) {
        this.streamId = streamId;
        this.tsMicros = tsMicros;
        this.deviceIds = deviceIds;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getTsMicros() {
        return tsMicros;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }
}
