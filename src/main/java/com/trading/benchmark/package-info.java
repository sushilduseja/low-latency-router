/**
 * Benchmark and demonstration classes for comparing standard and reuse-oriented
 * allocation modes in a high-throughput trading system.
 * <p>
 * The classes in this package include:
 * <ul>
 *   <li>ObjectPoolReference - Demonstrates how object pooling can reduce GC pressure</li>
 *   <li>StringInternReference - Shows how string interning avoids duplicate strings</li>
 *   <li>ThreadAffinityReference - Demonstrates thread-to-core pinning for reduced jitter</li>
 *   <li>DirectBufferReference - Shows how to use off-heap memory with DirectByteBuffers</li>
 * </ul>
 * <p>
 * Note: These classes are not used by TradingRouterBenchmarkApp and are kept for
 * educational purposes.
 * <p>
 * These classes together demonstrate various techniques to achieve consistent
 * low-latency performance in Java applications.
 */
package com.trading.benchmark;
