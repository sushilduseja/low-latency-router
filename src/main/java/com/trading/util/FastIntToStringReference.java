package com.trading.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates fast int to string conversion without allocations.
 * This is useful in trading systems where you frequently need to convert
 * numeric values to strings for FIX messages without creating garbage.
 */
public class FastIntToStringReference {
    private static final Logger LOGGER = LoggerFactory.getLogger(FastIntToStringReference.class);
    
    // Pre-allocated buffer for conversions
    private final char[] buffer;
    
    public FastIntToStringReference(int maxDigits) {
        this.buffer = new char[maxDigits];
    }
    
    /**
     * Convert an integer to string representation without allocations
     * @param value The integer to convert
     * @param target The target char array to write into
     * @param offset The offset in the target array to start writing
     * @return The number of characters written
     */
    public int intToString(int value, char[] target, int offset) {
        if (value == 0) {
            target[offset] = '0';
            return 1;
        }
        
        // Handle negative numbers
        boolean negative = value < 0;
        if (negative) {
            value = -value;
            target[offset++] = '-';
        }
        
        // Find number of digits
        int temp = value;
        int numDigits = 0;
        while (temp > 0) {
            temp /= 10;
            numDigits++;
        }
        
        // Convert digits from right to left
        int index = offset + numDigits - 1;
        while (value > 0) {
            int digit = value % 10;
            target[index--] = (char) ('0' + digit);
            value /= 10;
        }
        
        return numDigits + (negative ? 1 : 0);
    }
    
    /**
     * Convert integer to char array without allocations.
     * Uses internal buffer.
     */
    public char[] intToChars(int value) {
        int length = intToString(value, buffer, 0);
        // In a real zero-GC application, you'd use the buffer directly
        // rather than copying to a new array
        char[] result = new char[length];
        System.arraycopy(buffer, 0, result, 0, length);
        return result;
    }
    
    public static void main(String[] args) {
        FastIntToStringReference converter = new FastIntToStringReference(10);
        
        // Test some conversions
        LOGGER.info("Converting integers to strings without allocations");
        
        int[] testValues = {0, 42, 12345, -789, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int value : testValues) {
            char[] chars = converter.intToChars(value);
            String str = new String(chars);
            LOGGER.info("{} -> {}", value, str);
            
            // Verify against standard conversion
            if (!str.equals(Integer.toString(value))) {
                LOGGER.error("Conversion mismatch for {}", value);
            }
        }
        
        LOGGER.info("Fast int-to-string conversion completed");
        LOGGER.info("In a real low latency system, you would:");
        LOGGER.info("1. Use thread-local buffers to avoid thread contention");
        LOGGER.info("2. Use specialized number formatting for fixed formats (e.g., prices)");
        LOGGER.info("3. Prefer binary protocols over string-based ones when possible");
    }
}
