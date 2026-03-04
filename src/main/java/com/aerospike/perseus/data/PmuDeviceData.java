package com.aerospike.perseus.data;

import com.aerospike.client.Value;

import java.util.Map;

/**
 * One device's worth of PMU data for a single frame.
 * Maps directly to the per-device record model in the Hitachi app.
 */
public class PmuDeviceData {

    private final String deviceId;
    private final Map<Value, Value> cxMap;  // globalIdx → [real, imag, quality]
    private final Map<Value, Value> rlMap;  // globalIdx → [value, quality]

    public PmuDeviceData(String deviceId, Map<Value, Value> cxMap, Map<Value, Value> rlMap) {
        this.deviceId = deviceId;
        this.cxMap = cxMap;
        this.rlMap = rlMap;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Map<Value, Value> getCxMap() {
        return cxMap;
    }

    public Map<Value, Value> getRlMap() {
        return rlMap;
    }
}
