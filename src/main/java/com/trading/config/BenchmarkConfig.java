package com.trading.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class BenchmarkConfig {
    public static final int WARMUP_ROUNDS = 3;
    public static final int MEASURE_ROUNDS = 5;
    public static final int ORDERS_PER_ROUND = 50_000;
    public static final int GC_PRESSURE_OBJECTS = 5_000;
    public static final int ORDER_POOL_SIZE = 20;
    public static final boolean STANDARD_GC_PRESSURE = true;
    public static final boolean REUSE_GC_PRESSURE = false;
    public static final Path REPORT_DIR = Paths.get("build", "reports");

    private BenchmarkConfig() {
    }
}
