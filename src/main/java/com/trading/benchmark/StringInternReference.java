package com.trading.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Demonstrates String interning as a zero-GC technique to avoid duplicate strings.
 * This is useful in FIX protocol where many field values repeat frequently.
 */
public class StringInternReference {
    private static final Logger LOGGER = LoggerFactory.getLogger(StringInternReference.class);
    
    private final Map<String, String> stringPool = new HashMap<>();
    
    /**
     * Return an interned string - either from the pool if it exists,
     * or add the new string to the pool
     */
    public String intern(String str) {
        if (str == null) {
            return null;
        }
        
        String existing = stringPool.get(str);
        if (existing != null) {
            return existing;
        }
        
        // In a production environment, you would need to handle concurrency
        stringPool.put(str, str);
        return str;
    }
    
    public int getPoolSize() {
        return stringPool.size();
    }
    
    public static void main(String[] args) {
        StringInternReference internRef = new StringInternReference();
        
        // Simulate processing FIX messages with repeating values
        String[] symbols = {"AAPL", "MSFT", "GOOGL", "AMZN"};
        String[] clients = {"CLIENT1", "CLIENT2", "CLIENT3"};
        
        int totalStrings = 0;
        int uniqueStrings = 0;
        
        // Process 1000 orders with repeating values
        for (int i = 0; i < 1000; i++) {
            String symbol = symbols[i % symbols.length];
            String internedSymbol = internRef.intern(symbol);
            
            String client = clients[i % clients.length];
            String internedClient = internRef.intern(client);
            
            // Unique order ID for each order
            String orderId = "ORDER-" + i;
            String internedOrderId = internRef.intern(orderId);
            
            totalStrings += 3;  // We processed 3 strings
            uniqueStrings = internRef.getPoolSize();
        }
        
        LOGGER.info("Total strings processed: {}", totalStrings);
        LOGGER.info("Unique strings in pool: {}", uniqueStrings);
        LOGGER.info("Memory saving: {} strings", totalStrings - uniqueStrings);
    }
}
