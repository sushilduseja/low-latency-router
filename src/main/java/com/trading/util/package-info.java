/**
 * Utility classes demonstrating various zero-GC techniques that can be
 * applied in low latency trading systems.
 * <p>
 * The classes in this package show:
 * <ul>
 *   <li>Fast integer to string conversion without allocations</li>
 *   <li>Using Java 21 record classes for efficient immutable objects</li>
 * </ul>
 * <p>
 * These techniques help reduce or eliminate garbage creation which
 * in turn reduces GC pressure and improves latency consistency.
 */
package com.trading.util;
