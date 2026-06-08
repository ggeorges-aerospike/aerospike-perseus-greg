package com.aerospike.perseus.configurations;

import com.aerospike.perseus.configurations.pojos.RangeQueryConfiguration;

public class TestConfiguration {
    public int perseusId;

    // --- generic workload (kept for compatibility with the main-branch config) ---
    public Integer recordSize;
    public Integer readBatchSize;
    public Integer writeBatchSize;
    public Double readHitRatio;
    public Boolean stringIndex;
    public Boolean numericIndex;
    public Boolean geoSpatialIndex;
    public Boolean udfAggregation;
    public RangeQueryConfiguration rangeQueryConfiguration;
    public Boolean rangeQuery;

    // --- Cognitiv kepler model knobs (see COGNITIV_DATA_MODEL.md) ---
    public Integer keplerAvgSegmentsPerPerson;   // pseg: entries per person record
    public Integer keplerSegmentUniverse;        // pseg/url: distinct user_segment_id space
    public Integer keplerAvgProvidersPerUrl;     // url: providers per url record
    public Integer keplerProviderUniverse;       // url: distinct provider_id space
    public Integer keplerAvgSegmentsPerUrl;      // url: size of segments list<int>
    public Integer keplerTypeUniverse;           // seg: distinct segment_type_id space
    public Integer keplerAvgTypesPerIdentity;    // seg: records per identity
    public Integer keplerInnerSegmentsPerType;   // seg: size of segments map<int,bigint>
    public Integer keplerTtlSeconds;             // kepler per-record TTL (0 = none)

    // --- Cognitiv corvus model knobs ---
    public Integer corvusSmallBlobBytes;         // ~800
    public Integer corvusLargeBlobBytes;         // ~30000
    public Double corvusLargeBlobRatio;          // 0.4
    public Integer corvusTtlSeconds;             // 172800 (2 days) — confirm
}
