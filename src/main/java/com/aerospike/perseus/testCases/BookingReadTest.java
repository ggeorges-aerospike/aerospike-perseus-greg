package com.aerospike.perseus.testCases;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.perseus.data.generators.BookingGenerator;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The customer-facing read mix from the airline-demo dashboard story:
 * 45% booking lookups, 40% passenger profiles, 15% flight detail — all
 * single-record gets by string key. Booking reads prefer this instance's
 * own freshly-written range, falling back to the seeded range.
 */
public class BookingReadTest extends Test<Long> {

    private static final String SET_PASSENGERS = "passengers";
    private static final String SET_FLIGHTS = "flights";

    private final BookingGenerator generator;
    private final int seededBookings;
    private final int passengerCount;
    private final int flightCount;

    /** Trivial driver iterator — Test<T> pulls from a provider; values are unused. */
    private static final Iterator<Long> TICKS = new Iterator<>() {
        public boolean hasNext() { return true; }
        public Long next() { return 0L; }
    };

    public BookingReadTest(TestCaseConstructorArguments arguments, BookingGenerator generator,
                           int seededBookings, int passengerCount, int flightCount) {
        super(arguments, TICKS);
        this.generator = generator;
        this.seededBookings = Math.max(seededBookings, 1);
        this.passengerCount = Math.max(passengerCount, 1);
        this.flightCount = Math.max(flightCount, 1);
    }

    @Override
    protected void execute(Long ignoredTick) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try {
            int dice = rnd.nextInt(100);
            if (dice < 45) {
                long written = generator.written();
                String id = written > 0
                        ? "B" + (generator.getIdOffset() + rnd.nextLong(written))
                        : "B" + rnd.nextInt(seededBookings);
                client.get(null, new Key(namespace, setName, id));
            } else if (dice < 85) {
                client.get(null, new Key(namespace, SET_PASSENGERS, "P" + rnd.nextInt(passengerCount)));
            } else {
                client.get(null, new Key(namespace, SET_FLIGHTS, "F" + rnd.nextInt(flightCount)));
            }
        } catch (AerospikeException ignored) {
            // transient during failover — keep driving load
        }
    }

    public String[] getHeader() {
        return "Booking\nRead".split("\n");
    }
}
