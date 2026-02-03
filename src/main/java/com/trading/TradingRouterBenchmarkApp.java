package com.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trading.core.BenchmarkRunner;

public final class TradingRouterBenchmarkApp {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradingRouterBenchmarkApp.class);

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

        BenchmarkRunner.run(mode);
    }
}
