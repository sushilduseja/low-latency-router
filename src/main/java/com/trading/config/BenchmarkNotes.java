package com.trading.config;

import java.util.List;

public final class BenchmarkNotes {
    public static final List<String> NOTES = List.of(
            "Object pooling and reuse reduces allocation pressure",
            "Pre-sized collections avoid resize/rehash operations",
            "Mutable objects update state without new allocations",
            "ZGC tuning minimizes pause times for better tail latencies",
            "Direct ByteBuffers enable off-heap storage"
    );

    private BenchmarkNotes() {
    }
}
