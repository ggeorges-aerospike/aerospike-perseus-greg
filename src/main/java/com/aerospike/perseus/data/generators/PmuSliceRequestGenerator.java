package com.aerospike.perseus.data.generators;

import com.aerospike.perseus.data.PmuSliceRequest;
import com.aerospike.perseus.data.PmuTimestampTracker;

/**
 * Generates random slice read requests from the timestamp tracker.
 * Waits for writes to populate the tracker before generating requests.
 */
public class PmuSliceRequestGenerator extends BaseGenerator<PmuSliceRequest> {

    private final PmuTimestampTracker tracker;

    public PmuSliceRequestGenerator(PmuTimestampTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public PmuSliceRequest next() {
        // Spin-wait until writes have populated the tracker
        while (!tracker.hasEntries()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return tracker.randomSliceRequest();
    }
}
