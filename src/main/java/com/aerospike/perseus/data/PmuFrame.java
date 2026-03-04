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
    private final int localStreamIndex;

    public PmuFrame(String streamId, long tsMicros, List<PmuDeviceData> devices) {
        this(streamId, tsMicros, devices, 0);
    }

    public PmuFrame(String streamId, long tsMicros, List<PmuDeviceData> devices, int localStreamIndex) {
        this.streamId = streamId;
        this.tsMicros = tsMicros;
        this.devices = devices;
        this.localStreamIndex = localStreamIndex;
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

    public int getLocalStreamIndex() {
        return localStreamIndex;
    }
}
