package com.aerospike.perseus.data;

import com.aerospike.client.Bin;

public class BlueCatRecord {

    private final long key;
    private final Bin[] bins;

    public BlueCatRecord(long key, Bin[] bins) {
        this.key = key;
        this.bins = bins;
    }

    public long getKey() {
        return key;
    }

    public Bin[] getBins() {
        return bins;
    }
}
