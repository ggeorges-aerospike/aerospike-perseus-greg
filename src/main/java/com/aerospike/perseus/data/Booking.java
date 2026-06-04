package com.aerospike.perseus.data;

import com.aerospike.client.Bin;

/** An airline booking record (dashboard demo workload). */
public class Booking {
    public final String id;
    public final Bin[] bins;

    public Booking(String id, Bin[] bins) {
        this.id = id;
        this.bins = bins;
    }
}
