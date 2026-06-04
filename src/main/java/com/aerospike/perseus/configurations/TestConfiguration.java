package com.aerospike.perseus.configurations;

import com.aerospike.perseus.configurations.pojos.RangeQueryConfiguration;

public class TestConfiguration {
    public Integer recordSize;
    public Integer readBatchSize;
    public Integer writeBatchSize;
    public Integer blueCatWriteBatchSize;
    public Double readHitRatio;
    public Boolean stringIndex;
    public Boolean numericIndex;
    public Boolean geoSpatialIndex;
    public Boolean udfAggregation;
    public RangeQueryConfiguration rangeQueryConfiguration;
    public Boolean rangeQuery;
    public int perseusId;

    // PMU workload configuration
    public Integer pmuStreamCount = 10;
    public Integer pmuStreamStartIndex = 1;
    public String pmuStreamPrefix = "pdc";
    public Integer pmuDeviceCount = 6;
    public Integer pmuComplexCount = 137;
    public Integer pmuRealCount = 144;
    public Integer pmuFps = 200;
    public Integer pmuTtlSeconds = 43200;  // 12 hours

    // C37.118 TCP streaming configuration
    public String c37118ReceiverHost = "localhost";
    public Integer c37118ReceiverPort = 4712;

    // Airline booking workload configuration (airline-demo dashboard)
    public String bookingSet = "bookings";
    public Integer bookingPassengerCount = 200000;
    public Integer bookingFlightCount = 5000;
    public Integer bookingSeededCount = 300000;
    public Integer bookingCounterBatch = 100;
}
