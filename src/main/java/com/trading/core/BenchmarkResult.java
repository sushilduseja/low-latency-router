package com.trading.core;

import java.util.Arrays;

import com.trading.ExecutionMode;
import com.trading.config.BenchmarkConfig;

public record BenchmarkResult(
        ExecutionMode mode,
        long[] durationsNanos,
        int[] allocationsPerRound,
        double averageMs,
        double minMs,
        double medianMs,
        double p95Ms,
        double maxMs,
        int averageAllocations,
        double throughputPerSecond) {

    static BenchmarkResult from(
            ExecutionMode mode,
            long[] durationsNanos,
            int[] allocationsPerRound,
            long totalNanos) {
        long[] sorted = durationsNanos.clone();
        Arrays.sort(sorted);

        double averageMs = BenchmarkRunner.nanosToMillis(totalNanos / (double) durationsNanos.length);
        double minMs = BenchmarkRunner.nanosToMillis(sorted[0]);
        double maxMs = BenchmarkRunner.nanosToMillis(sorted[sorted.length - 1]);
        double medianMs = BenchmarkRunner.nanosToMillis(sorted[sorted.length / 2]);
        double p95Ms = BenchmarkRunner.nanosToMillis(sorted[(int) (sorted.length * 0.95)]);

        int avgAllocations = 0;
        for (int alloc : allocationsPerRound) {
            avgAllocations += alloc;
        }
        avgAllocations = Math.round(avgAllocations / (float) allocationsPerRound.length);

        double throughput = BenchmarkConfig.ORDERS_PER_ROUND / (averageMs / 1000.0);

        return new BenchmarkResult(
                mode,
                durationsNanos,
                allocationsPerRound,
                averageMs,
                minMs,
                medianMs,
                p95Ms,
                maxMs,
                avgAllocations,
                throughput);
    }
}
