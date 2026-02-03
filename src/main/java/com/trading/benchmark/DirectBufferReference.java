package com.trading.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class DirectBufferReference {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectBufferReference.class);

    private static final int ORDER_SIZE = 29;

    private final ByteBuffer buffer;
    private final int capacity;

    public DirectBufferReference(int orderCapacity) {
        this.capacity = orderCapacity;
        this.buffer = ByteBuffer.allocateDirect(ORDER_SIZE * orderCapacity);
        LOGGER.info("Created direct buffer with capacity for {} orders ({} bytes)",
                orderCapacity, ORDER_SIZE * orderCapacity);
    }

    public void writeOrder(
            int index,
            long orderId,
            int symbolCode,
            byte type,
            double price,
            int quantity,
            int clientCode) {
        if (index >= capacity) {
            throw new IndexOutOfBoundsException("Buffer index out of bounds: " + index);
        }

        buffer.position(index * ORDER_SIZE);
        buffer.putLong(orderId);
        buffer.putInt(symbolCode);
        buffer.put(type);
        buffer.putDouble(price);
        buffer.putInt(quantity);
        buffer.putInt(clientCode);
    }

    public void readOrder(int index) {
        if (index >= capacity) {
            throw new IndexOutOfBoundsException("Buffer index out of bounds: " + index);
        }

        buffer.position(index * ORDER_SIZE);

        long orderId = buffer.getLong();
        int symbolCode = buffer.getInt();
        byte type = buffer.get();
        double price = buffer.getDouble();
        int quantity = buffer.getInt();
        int clientCode = buffer.getInt();

        LOGGER.info("Read order - id: {}, symbol: {}, type: {}, price: {}, qty: {}, client: {}",
                orderId, symbolCode, type, price, quantity, clientCode);
    }

    public static void main(String[] args) {
        DirectBufferReference bufferRef = new DirectBufferReference(1_000_000);

        for (int i = 0; i < 10; i++) {
            int symbolCode = ('A' << 24) | ('A' << 16) | ('P' << 8) | 'L';
            bufferRef.writeOrder(i, 1000 + i, symbolCode, (byte) 1, 150.0 + i, 100 + i, 42);
        }

        for (int i = 0; i < 5; i++) {
            bufferRef.readOrder(i);
        }

        LOGGER.info("Direct buffer reference completed");
        LOGGER.info("Benefits of direct buffers in low latency trading:");
        LOGGER.info("1. No GC overhead for large data structures");
        LOGGER.info("2. Memory layout optimized for sequential access");
        LOGGER.info("3. Potential for zero-copy operations with network/disk I/O");
        LOGGER.info("4. Predictable memory access patterns");
    }
}
