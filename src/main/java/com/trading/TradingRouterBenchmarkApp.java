package com.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class TradingRouterBenchmarkApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradingRouterBenchmarkApp.class);

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURE_ROUNDS = 5;
    private static final int ORDERS_PER_ROUND = 50_000;
    private static final int GC_PRESSURE_OBJECTS = 5_000;
    private static final int ORDER_POOL_SIZE = 20;

    private enum ExecutionMode {
        STANDARD("standard"),
        ZERO_GC("zerogc");

        private final String cliName;

        ExecutionMode(String cliName) {
            this.cliName = cliName;
        }

        String cliName() {
            return cliName;
        }

        static ExecutionMode fromCli(String rawValue) {
            for (ExecutionMode mode : values()) {
                if (mode.cliName.equalsIgnoreCase(rawValue)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown mode: " + rawValue);
        }
    }

    public static void main(String[] args) {
        LOGGER.info("=== Low Latency Trading Router Benchmark ===");

        if (args.length != 1) {
            LOGGER.error("Usage: <standard|zerogc>");
            System.exit(1);
        }

        ExecutionMode mode;
        try {
            mode = ExecutionMode.fromCli(args[0]);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("{}", ex.getMessage());
            LOGGER.error("Use 'standard' or 'zerogc'.");
            System.exit(1);
            return;
        }

        LOGGER.info("Mode: {}", mode.cliName());
        runBenchmark(mode);
    }

    private static void runBenchmark(ExecutionMode mode) {
        LOGGER.info("Warmup: {} rounds", WARMUP_ROUNDS);
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            if (mode == ExecutionMode.ZERO_GC) {
                runReuseAllocation(ORDERS_PER_ROUND / 10, false);
            } else {
                runStandardAllocation(ORDERS_PER_ROUND / 10, false);
            }
        }

        LOGGER.info("Measurement: {} rounds", MEASURE_ROUNDS);
        if (mode == ExecutionMode.ZERO_GC) {
            LOGGER.info("ZeroGC: object reuse with ZGC-friendly settings");
            benchmarkReuseAllocation();
        } else {
            LOGGER.info("Standard: regular allocation patterns");
            benchmarkStandardAllocation();
        }

        logSummary();
    }

    private static void benchmarkStandardAllocation() {
        long[] roundDurations = new long[MEASURE_ROUNDS];
        long totalNanos = 0L;

        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long startTime = System.nanoTime();
            int allocations = runStandardAllocation(ORDERS_PER_ROUND, false);
            long elapsedNanos = System.nanoTime() - startTime;

            roundDurations[i] = elapsedNanos;
            totalNanos += elapsedNanos;

            LOGGER.info("Round {}: {} ms | allocations: {}",
                    i + 1, formatMillis(elapsedNanos), allocations);
        }

        logStatistics(roundDurations, totalNanos);
    }

    private static void benchmarkReuseAllocation() {
        long[] roundDurations = new long[MEASURE_ROUNDS];
        long totalNanos = 0L;

        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long startTime = System.nanoTime();
            int allocations = runReuseAllocation(ORDERS_PER_ROUND, false);
            long elapsedNanos = System.nanoTime() - startTime;

            roundDurations[i] = elapsedNanos;
            totalNanos += elapsedNanos;

            LOGGER.info("Round {}: {} ms | allocations: {}",
                    i + 1, formatMillis(elapsedNanos), allocations);
        }

        logStatistics(roundDurations, totalNanos);
    }

    private static void logStatistics(long[] times, long totalNanos) {
        Arrays.sort(times);

        double averageMs = nanosToMillis(totalNanos / (double) times.length);
        double minMs = nanosToMillis(times[0]);
        double maxMs = nanosToMillis(times[times.length - 1]);
        double medianMs = nanosToMillis(times[times.length / 2]);
        double p95Ms = nanosToMillis(times[(int) (times.length * 0.95)]);

        LOGGER.info("Performance (ms)");
        LOGGER.info("Min    : {}", formatMillis(minMs));
        LOGGER.info("Median : {}", formatMillis(medianMs));
        LOGGER.info("Average: {}", formatMillis(averageMs));
        LOGGER.info("P95    : {}", formatMillis(p95Ms));
        LOGGER.info("Max    : {}", formatMillis(maxMs));
    }

    private static int runStandardAllocation(int orderCount, boolean logProgress) {
        if (logProgress) {
            LOGGER.info("Processing {} orders with new allocations...", orderCount);
        }

        int allocations = 0;
        List<GarbagePressurePayload> garbage = new ArrayList<>();
        int progressInterval = Math.max(orderCount / 4, 1);

        for (int i = 0; i < orderCount; i++) {
            SimulatedOrder order = new SimulatedOrder("ORDER-" + i);
            allocations++;

            order.setPrice(100.0 + (i % 10));
            order.setQuantity(100 + (i % 50));
            order.setTimestamp(System.nanoTime());

            simulateExecution(order);

            if (logProgress && i % 100 == 0) {
                for (int j = 0; j < GC_PRESSURE_OBJECTS; j++) {
                    garbage.add(new GarbagePressurePayload("data-" + j, j));
                    allocations++;
                }
                if (garbage.size() > GC_PRESSURE_OBJECTS * 10) {
                    garbage.subList(0, GC_PRESSURE_OBJECTS * 5).clear();
                }
            }

            if (logProgress && i % progressInterval == 0 && i > 0) {
                LOGGER.info("Processed {} orders", i);
            }
        }

        if (logProgress) {
            LOGGER.info("Total objects allocated: {}", allocations);
        }

        return allocations;
    }

    private static int runReuseAllocation(int orderCount, boolean logProgress) {
        if (logProgress) {
            LOGGER.info("Processing {} orders with object reuse...", orderCount);
        }

        SimulatedOrder[] orderPool = new SimulatedOrder[ORDER_POOL_SIZE];
        for (int i = 0; i < orderPool.length; i++) {
            orderPool[i] = new SimulatedOrder("");
        }

        int allocations = orderPool.length;
        Map<String, SymbolMetrics> metricsBySymbol = new HashMap<>(16);
        int progressInterval = Math.max(orderCount / 4, 1);

        for (int i = 0; i < orderCount; i++) {
            SimulatedOrder order = orderPool[i % orderPool.length];
            order.reset("ORDER-" + i, 100.0 + (i % 10), 100 + (i % 50));
            order.setTimestamp(System.nanoTime());

            simulateExecution(order);

            String symbol = "SYM" + (i % 10);
            SymbolMetrics metrics = metricsBySymbol.get(symbol);
            if (metrics == null) {
                metrics = new SymbolMetrics();
                metricsBySymbol.put(symbol, metrics);
                allocations++;
            }
            metrics.updateWith(order);

            if (logProgress && i % progressInterval == 0 && i > 0) {
                LOGGER.info("Processed {} orders", i);
            }
        }

        if (logProgress) {
            LOGGER.info("Total objects allocated: {}", allocations);
        }

        return allocations;
    }

    private static void simulateExecution(SimulatedOrder order) {
        double notional = order.getPrice() * order.getQuantity();
        double fee = notional * 0.0001;

        double executedValue = 0;
        for (int i = 0; i < 100; i++) {
            executedValue += (notional + fee) * (1 + Math.sin(i * 0.01) * 0.0001);
        }

        order.setExecutedValue(executedValue);
    }

    private static void logSummary() {
        LOGGER.info("Notes");
        LOGGER.info("1. Object pooling and reuse reduces allocation pressure");
        LOGGER.info("2. Pre-sized collections avoid resize/rehash operations");
        LOGGER.info("3. Mutable objects update state without new allocations");
        LOGGER.info("4. ZGC tuning minimizes pause times for better tail latencies");
        LOGGER.info("5. Direct ByteBuffers enable off-heap storage");
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static String formatMillis(double millis) {
        return String.format(Locale.US, "%.2f", millis);
    }

    private static String formatMillis(long nanos) {
        return formatMillis(nanosToMillis(nanos));
    }

    private static final class SimulatedOrder {
        private String id;
        private double price;
        private int quantity;
        private double executedValue;
        private long timestamp;

        SimulatedOrder(String id) {
            this.id = id;
        }

        void reset(String id, double price, int quantity) {
            this.id = id;
            this.price = price;
            this.quantity = quantity;
            this.executedValue = 0.0;
        }

        String getId() {
            return id;
        }

        double getPrice() {
            return price;
        }

        void setPrice(double price) {
            this.price = price;
        }

        int getQuantity() {
            return quantity;
        }

        void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        double getExecutedValue() {
            return executedValue;
        }

        void setExecutedValue(double value) {
            this.executedValue = value;
        }

        void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        long getTimestamp() {
            return timestamp;
        }
    }

    private static final class SymbolMetrics {
        private int count;
        private double totalValue;
        private double minPrice;
        private double maxPrice;

        SymbolMetrics() {
            this.minPrice = Double.POSITIVE_INFINITY;
            this.maxPrice = Double.NEGATIVE_INFINITY;
        }

        void updateWith(SimulatedOrder order) {
            count++;
            totalValue += order.getExecutedValue();
            minPrice = Math.min(minPrice, order.getPrice());
            maxPrice = Math.max(maxPrice, order.getPrice());
        }
    }

    private static final class GarbagePressurePayload {
        private final String data;
        private final int value;
        private final byte[] buffer;

        GarbagePressurePayload(String data, int value) {
            this.data = data;
            this.value = value;
            this.buffer = new byte[ThreadLocalRandom.current().nextInt(100, 1000)];
        }
    }
}
