package com.aerospike.perseus.testCases.cognitiv;

import java.util.Iterator;

/**
 * A constant driver iterator. Test&lt;T&gt; pulls one value from its provider per
 * op; read tests don't need generated values (they pick their own keys), so they
 * use this shared infinite stream of 0L.
 */
public final class Ticks {
    public static final Iterator<Long> INSTANCE = new Iterator<>() {
        public boolean hasNext() { return true; }
        public Long next() { return 0L; }
    };
    private Ticks() {}
}
