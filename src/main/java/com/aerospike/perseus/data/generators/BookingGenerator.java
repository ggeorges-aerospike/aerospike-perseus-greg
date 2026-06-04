package com.aerospike.perseus.data.generators;

import com.aerospike.client.Bin;
import com.aerospike.perseus.data.Booking;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates airline booking records matching the airline-demo dashboard schema
 * (sets: bookings / passengers / flights, see airline-demo project). Each
 * Perseus instance writes its own id range so multiple loaders never collide
 * with each other or with the dashboard app's bookings.
 */
public class BookingGenerator implements Iterator<Booking> {

    private final long idOffset;
    private final int passengerCount;
    private final int flightCount;
    private final AtomicLong seq = new AtomicLong();

    private static final String[] CABINS = {"ECONOMY", "ECONOMY", "ECONOMY", "ECONOMY", "PREMIUM", "BUSINESS"};

    public BookingGenerator(int perseusId, int passengerCount, int flightCount) {
        // app + seeded bookings live well below 1B; each loader gets its own billion-range
        this.idOffset = (perseusId + 1) * 1_000_000_000L;
        this.passengerCount = Math.max(passengerCount, 1);
        this.flightCount = Math.max(flightCount, 1);
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Booking next() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long id = idOffset + seq.getAndIncrement();

        StringBuilder pnr = new StringBuilder(6);
        for (int c = 0; c < 6; c++) pnr.append((char) ('A' + rnd.nextInt(26)));

        String bookingId = "B" + id;
        return new Booking(bookingId, new Bin[]{
                new Bin("id", bookingId),
                new Bin("pnr", pnr.toString()),
                new Bin("paxId", "P" + rnd.nextInt(passengerCount)),
                new Bin("flightId", "F" + rnd.nextInt(flightCount)),
                new Bin("seat", (1 + rnd.nextInt(42)) + String.valueOf((char) ('A' + rnd.nextInt(6)))),
                new Bin("cabin", CABINS[rnd.nextInt(CABINS.length)]),
                new Bin("status", "CONFIRMED"),
                new Bin("createdAt", System.currentTimeMillis()),
                new Bin("mrt", 0)
        });
    }

    public long getIdOffset() {
        return idOffset;
    }

    /** Number of bookings generated so far by this instance. */
    public long written() {
        return seq.get();
    }
}
