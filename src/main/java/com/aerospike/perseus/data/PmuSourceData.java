package com.aerospike.perseus.data;

import com.aerospike.client.Value;

import java.util.Map;

/**
 * One source's (PMU IDCODE) worth of PMU data for a single frame.
 * Maps directly to the per-source record model in the Hitachi app.
 */
public class PmuSourceData {

    private final String sourceId;
    private final Map<Value, Value> cxMap;  // globalIdx → [real, imag, quality]
    private final Map<Value, Value> rlMap;  // globalIdx → [value, quality]

    public PmuSourceData(String sourceId, Map<Value, Value> cxMap, Map<Value, Value> rlMap) {
        this.sourceId = sourceId;
        this.cxMap = cxMap;
        this.rlMap = rlMap;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Map<Value, Value> getCxMap() {
        return cxMap;
    }

    public Map<Value, Value> getRlMap() {
        return rlMap;
    }
}
