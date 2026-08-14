package com.aerospike.perseus.testCases.wyllo;

import java.util.Iterator;

/**
 * A constant driver iterator. Test&lt;T&gt; pulls one value from its provider per op;
 * the batch test pulls its own values from the generator inside execute(), so it
 * uses this shared infinite stream of 0L as a driver instead.
 */
public final class Ticks {
    public static final Iterator<Long> INSTANCE = new Iterator<>() {
        public boolean hasNext() { return true; }
        public Long next() { return 0L; }
    };
    private Ticks() {}
}
