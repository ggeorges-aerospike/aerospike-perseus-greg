package com.aerospike.perseus.data;

import java.util.List;

/**
 * A single PMU frame: one timestamp across all devices for one stream.
 * Each frame produces N device records (one per device).
 */
public class PmuFrame {

    private final String streamId;
    private final long tsMicros;
    private final List<PmuDeviceData> devices;

    public PmuFrame(String streamId, long tsMicros, List<PmuDeviceData> devices) {
        this.streamId = streamId;
        this.tsMicros = tsMicros;
        this.devices = devices;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getTsMicros() {
        return tsMicros;
    }

    public List<PmuDeviceData> getDevices() {
        return devices;
    }
}
