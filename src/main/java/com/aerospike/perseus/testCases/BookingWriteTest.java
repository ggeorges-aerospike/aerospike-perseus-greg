package com.aerospike.perseus.testCases;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.perseus.data.Booking;
import com.aerospike.perseus.data.generators.BookingGenerator;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes airline bookings (string keys, dashboard demo schema) and keeps the
 * dashboard's sharded booking counter in sync — incremented in batches so the
 * counter costs ~1% extra ops at full rate.
 *
 * Aerospike exceptions are swallowed (counted by the server / visible in
 * Grafana) instead of bubbling to Test.action(), which would zero the thread
 * pool — we WANT traffic to keep flowing through the node-failure scene.
 */
public class BookingWriteTest extends Test<Booking> {

    private static final String COUNTER_SET = "counters";
    private static final int COUNTER_SHARDS = 32;   // shard 0 reserved for the app's MRT stream

    private final int counterBatch;
    private final AtomicLong sinceLastCounterPush = new AtomicLong();
    private final WritePolicy writePolicy = new WritePolicy();

    public BookingWriteTest(TestCaseConstructorArguments arguments, BookingGenerator generator, int counterBatch) {
        super(arguments, generator);
        this.counterBatch = Math.max(counterBatch, 1);
        this.writePolicy.sendKey = true;   // keys visible in data browsers (Voyager)
    }

    @Override
    protected void execute(Booking booking) {
        try {
            client.put(writePolicy, new Key(namespace, setName, booking.id), booking.bins);
            if (sinceLastCounterPush.incrementAndGet() % counterBatch == 0) {
                int shard = 1 + ThreadLocalRandom.current().nextInt(COUNTER_SHARDS - 1);
                client.add(writePolicy, new Key(namespace, COUNTER_SET, "bookings:" + shard),
                        new Bin("count", counterBatch));
            }
        } catch (AerospikeException ignored) {
            // transient during failover — keep driving load
        }
    }

    public String[] getHeader() {
        return "Booking\nWrite".split("\n");
    }
}
