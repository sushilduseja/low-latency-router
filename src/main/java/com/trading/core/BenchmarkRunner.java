package com.trading.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import com.trading.ExecutionMode;
import com.trading.config.BenchmarkConfig;
import com.trading.config.BenchmarkNotes;
import com.trading.report.HtmlReportBuilder;

public final class BenchmarkRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkRunner.class);

    public static void run(ExecutionMode mode) {
        LOGGER.info("Mode: {}", mode.cliName());
        BenchmarkResult result = runBenchmark(mode);
        persistResult(result);
        tryGenerateCombinedReport(result.mode());
    }

    public static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    public static String formatMillis(double millis) {
        return String.format(Locale.US, "%.2f", millis);
    }

    private static BenchmarkResult runBenchmark(ExecutionMode mode) {
        LOGGER.info("Warmup: {} rounds", BenchmarkConfig.WARMUP_ROUNDS);
        for (int i = 0; i < BenchmarkConfig.WARMUP_ROUNDS; i++) {
            if (mode == ExecutionMode.ZERO_GC) {
                runReuseAllocation(BenchmarkConfig.ORDERS_PER_ROUND / 10, false, BenchmarkConfig.REUSE_GC_PRESSURE);
            } else {
                runStandardAllocation(BenchmarkConfig.ORDERS_PER_ROUND / 10, false, BenchmarkConfig.STANDARD_GC_PRESSURE);
            }
        }

        LOGGER.info("Measurement: {} rounds", BenchmarkConfig.MEASURE_ROUNDS);
        if (mode == ExecutionMode.ZERO_GC) {
            LOGGER.info("ZeroGC: object reuse with ZGC-friendly settings");
            return benchmarkReuseAllocation();
        }

        LOGGER.info("Standard: regular allocation patterns");
        return benchmarkStandardAllocation();
    }

    private static BenchmarkResult benchmarkStandardAllocation() {
        long[] roundDurations = new long[BenchmarkConfig.MEASURE_ROUNDS];
        int[] allocations = new int[BenchmarkConfig.MEASURE_ROUNDS];
        long totalNanos = 0L;

        for (int i = 0; i < BenchmarkConfig.MEASURE_ROUNDS; i++) {
            long startTime = System.nanoTime();
            int allocated = runStandardAllocation(
                    BenchmarkConfig.ORDERS_PER_ROUND,
                    false,
                    BenchmarkConfig.STANDARD_GC_PRESSURE);
            long elapsedNanos = System.nanoTime() - startTime;

            roundDurations[i] = elapsedNanos;
            allocations[i] = allocated;
            totalNanos += elapsedNanos;

            LOGGER.info("Round {}: {} ms | allocations: {}",
                    i + 1, formatMillis(nanosToMillis(elapsedNanos)), allocated);
        }

        BenchmarkResult result = BenchmarkResult.from(
                ExecutionMode.STANDARD,
                roundDurations,
                allocations,
                totalNanos);
        logStatistics(result);
        return result;
    }

    private static BenchmarkResult benchmarkReuseAllocation() {
        long[] roundDurations = new long[BenchmarkConfig.MEASURE_ROUNDS];
        int[] allocations = new int[BenchmarkConfig.MEASURE_ROUNDS];
        long totalNanos = 0L;

        for (int i = 0; i < BenchmarkConfig.MEASURE_ROUNDS; i++) {
            long startTime = System.nanoTime();
            int allocated = runReuseAllocation(
                    BenchmarkConfig.ORDERS_PER_ROUND,
                    false,
                    BenchmarkConfig.REUSE_GC_PRESSURE);
            long elapsedNanos = System.nanoTime() - startTime;

            roundDurations[i] = elapsedNanos;
            allocations[i] = allocated;
            totalNanos += elapsedNanos;

            LOGGER.info("Round {}: {} ms | allocations: {}",
                    i + 1, formatMillis(nanosToMillis(elapsedNanos)), allocated);
        }

        BenchmarkResult result = BenchmarkResult.from(
                ExecutionMode.ZERO_GC,
                roundDurations,
                allocations,
                totalNanos);
        logStatistics(result);
        return result;
    }

    private static void logStatistics(BenchmarkResult result) {
        LOGGER.info("Performance (ms)");
        LOGGER.info("Min    : {}", formatMillis(result.minMs()));
        LOGGER.info("Median : {}", formatMillis(result.medianMs()));
        LOGGER.info("Average: {}", formatMillis(result.averageMs()));
        LOGGER.info("P95    : {}", formatMillis(result.p95Ms()));
        LOGGER.info("Max    : {}", formatMillis(result.maxMs()));
    }

    private static int runStandardAllocation(int orderCount, boolean logProgress, boolean gcPressureEnabled) {
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

            if (gcPressureEnabled && i % 100 == 0) {
                for (int j = 0; j < BenchmarkConfig.GC_PRESSURE_OBJECTS; j++) {
                    garbage.add(new GarbagePressurePayload("data-" + j, j));
                    allocations++;
                }
                if (garbage.size() > BenchmarkConfig.GC_PRESSURE_OBJECTS * 10) {
                    garbage.subList(0, BenchmarkConfig.GC_PRESSURE_OBJECTS * 5).clear();
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

    private static int runReuseAllocation(int orderCount, boolean logProgress, boolean gcPressureEnabled) {
        if (logProgress) {
            LOGGER.info("Processing {} orders with object reuse...", orderCount);
        }

        SimulatedOrder[] orderPool = new SimulatedOrder[BenchmarkConfig.ORDER_POOL_SIZE];
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

            if (gcPressureEnabled && i % 100 == 0) {
                for (int j = 0; j < BenchmarkConfig.GC_PRESSURE_OBJECTS; j++) {
                    allocations++;
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

    private static void simulateExecution(SimulatedOrder order) {
        double notional = order.getPrice() * order.getQuantity();
        double fee = notional * 0.0001;

        double executedValue = 0;
        for (int i = 0; i < 100; i++) {
            executedValue += (notional + fee) * (1 + Math.sin(i * 0.01) * 0.0001);
        }

        order.setExecutedValue(executedValue);
    }

    private static void persistResult(BenchmarkResult result) {
        Properties properties = new Properties();
        properties.setProperty("mode", result.mode().cliName());
        properties.setProperty("averageMs", Double.toString(result.averageMs()));
        properties.setProperty("medianMs", Double.toString(result.medianMs()));
        properties.setProperty("p95Ms", Double.toString(result.p95Ms()));
        properties.setProperty("maxMs", Double.toString(result.maxMs()));
        properties.setProperty("minMs", Double.toString(result.minMs()));
        properties.setProperty("allocations", Integer.toString(result.averageAllocations()));
        properties.setProperty("throughputPerSecond", Double.toString(result.throughputPerSecond()));

        Path path = BenchmarkConfig.REPORT_DIR.resolve("benchmark-" + result.mode().cliName() + ".properties");
        try {
            Files.createDirectories(BenchmarkConfig.REPORT_DIR);
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Benchmark result");
            }
        } catch (IOException ex) {
            LOGGER.error("Result persistence failed: {}", ex.getMessage());
        }
    }

    private static void tryGenerateCombinedReport(ExecutionMode completedMode) {
        ExecutionMode other = completedMode == ExecutionMode.STANDARD
                ? ExecutionMode.ZERO_GC
                : ExecutionMode.STANDARD;

        Path completedPath = BenchmarkConfig.REPORT_DIR.resolve("benchmark-" + completedMode.cliName() + ".properties");
        Path otherPath = BenchmarkConfig.REPORT_DIR.resolve("benchmark-" + other.cliName() + ".properties");

        if (!Files.exists(completedPath) || !Files.exists(otherPath)) {
            return;
        }

        BenchmarkResult standard = loadResult(BenchmarkConfig.REPORT_DIR.resolve("benchmark-standard.properties"));
        BenchmarkResult zeroGc = loadResult(BenchmarkConfig.REPORT_DIR.resolve("benchmark-zerogc.properties"));

        if (standard == null || zeroGc == null) {
            return;
        }

        LOGGER.info("Summary");
        LOGGER.info("Mode      | Avg (ms) | Median | P95   | Max   | Allocations | Throughput (orders/s)");
        LOGGER.info("{}", formatRow("standard", standard));
        LOGGER.info("{}", formatRow("zerogc", zeroGc));
        logNotes();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String html = HtmlReportBuilder.render(timestamp, standard, zeroGc);

        try {
            Path reportPath = BenchmarkConfig.REPORT_DIR.resolve("benchmark.html");
            Files.writeString(reportPath, html);
            LOGGER.info("Report: {}", reportPath.toAbsolutePath());
        } catch (IOException ex) {
            LOGGER.error("Report generation failed: {}", ex.getMessage());
        }
    }

    private static void logNotes() {
        LOGGER.info("Notes");
        for (int i = 0; i < BenchmarkNotes.NOTES.size(); i++) {
            LOGGER.info("{}. {}", i + 1, BenchmarkNotes.NOTES.get(i));
        }
    }

    private static BenchmarkResult loadResult(Path path) {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ex) {
            LOGGER.error("Result load failed: {}", ex.getMessage());
            return null;
        }

        return new BenchmarkResult(
                ExecutionMode.fromCli(properties.getProperty("mode", "standard")),
                new long[0],
                new int[0],
                parseDouble(properties, "averageMs"),
                parseDouble(properties, "minMs"),
                parseDouble(properties, "medianMs"),
                parseDouble(properties, "p95Ms"),
                parseDouble(properties, "maxMs"),
                parseInt(properties, "allocations"),
                parseDouble(properties, "throughputPerSecond"));
    }

    private static double parseDouble(Properties properties, String key) {
        return Double.parseDouble(properties.getProperty(key, "0"));
    }

    private static int parseInt(Properties properties, String key) {
        return Integer.parseInt(properties.getProperty(key, "0"));
    }

    private static String formatRow(String mode, BenchmarkResult result) {
        return String.format(
                Locale.US,
                "%-9s | %8.2f | %6.2f | %5.2f | %5.2f | %11d | %21.0f",
                mode,
                result.averageMs(),
                result.medianMs(),
                result.p95Ms(),
                result.maxMs(),
                result.averageAllocations(),
                result.throughputPerSecond());
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
