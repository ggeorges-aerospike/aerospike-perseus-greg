package com.aerospike.perseus.configurations;

import com.aerospike.perseus.configurations.pojos.RangeQueryConfiguration;

public class TestConfiguration {
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
    public int perseusId;

    // --- Wyllo feature-platform model knobs (see WYLLO_DATA_MODEL.md) ---
    public Integer wylloFeaturesPerEntity;   // features upserted per write
    public Integer wylloAvgWritesPerUser;    // writes before the user universe grows by one
    public Integer wylloBinUniverse;         // distinct card BINs
    public Integer wylloMaxBinsPerUser;      // bin: entity instances per user
    public Integer wylloMaxBinIpsPerUser;    // bin_ip: entity instances per user
    public Integer wylloMaxEmailsPerUser;    // email: entity instances per user
    public Integer wylloBinWeight;           // relative share of writes hitting bin
    public Integer wylloBinIpWeight;         // ... bin_ip
    public Integer wylloEmailWeight;         // ... email
    public Integer wylloVectorSize;          // features requested per model-scoring read
    public Integer wylloTtlSeconds;          // 0 = never expire (cold features have no TTL)
}
