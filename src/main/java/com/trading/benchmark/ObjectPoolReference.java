package com.trading.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ObjectPoolReference<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectPoolReference.class);

    private final Deque<T> pool;
    private final Supplier<T> factory;
    private final int maxPoolSize;
    private final AtomicInteger allocations = new AtomicInteger();
    private final AtomicInteger reuses = new AtomicInteger();

    public ObjectPoolReference(Supplier<T> factory, int initialSize, int maxPoolSize) {
        this.factory = factory;
        this.maxPoolSize = maxPoolSize;
        this.pool = new ArrayDeque<>(initialSize);

        for (int i = 0; i < initialSize; i++) {
            pool.add(factory.get());
            allocations.incrementAndGet();
        }
    }

    public T acquire() {
        T instance = pool.poll();
        if (instance == null) {
            instance = factory.get();
            allocations.incrementAndGet();
        } else {
            reuses.incrementAndGet();
        }
        return instance;
    }

    public void release(T instance) {
        if (pool.size() < maxPoolSize) {
            pool.offer(instance);
        }
    }

    public void logStats() {
        int totalAllocations = allocations.get();
        int totalReuses = reuses.get();
        double reuseRatio = (totalReuses * 100.0) / (totalAllocations + totalReuses);

        LOGGER.info("Object Pool Stats");
        LOGGER.info("Allocations: {}", totalAllocations);
        LOGGER.info("Reuses: {}", totalReuses);
        LOGGER.info("Pool Size: {}", pool.size());
        LOGGER.info("Reuse Ratio: {}%", String.format("%.2f", reuseRatio));
    }

    public static void main(String[] args) {
        ObjectPoolReference<PooledOrder> orderPool = new ObjectPoolReference<>(
                () -> new PooledOrder("", "", 0.0, 0),
                5,
                20);

        for (int i = 0; i < 100; i++) {
            PooledOrder order = orderPool.acquire();
            order.reset("ORDER-" + i, "AAPL", 150.0 + i, 100 + i);
            LOGGER.info("Processing: {}", order);
            orderPool.release(order);
        }

        orderPool.logStats();
    }

    private static class PooledOrder {
        private String id;
        private String symbol;
        private double price;
        private int quantity;

        private PooledOrder(String id, String symbol, double price, int quantity) {
            this.id = id;
            this.symbol = symbol;
            this.price = price;
            this.quantity = quantity;
        }

        private void reset(String id, String symbol, double price, int quantity) {
            this.id = id;
            this.symbol = symbol;
            this.price = price;
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return "Order{id='" + id + "', symbol='" + symbol + "', price=" + price + ", quantity=" + quantity + "}";
        }
    }
}
