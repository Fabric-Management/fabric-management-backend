package com.fabricmanagement.production.masterdata.fiber.domain;

/**
 * Fiber lifecycle status enumeration.
 *
 * <p>Represents the current state of a fiber in the production lifecycle.</p>
 *
 * <h2>Status Flow:</h2>
 * <pre>
 * NEW → IN_USE → EXHAUSTED
 *   ↓
 * OBSOLETE (can be set from any state)
 * </pre>
 */
public enum FiberStatus {
    
    /**
     * Newly created fiber, not yet used in production.
     */
    NEW,
    
    /**
     * Fiber is currently in use in production processes.
     */
    IN_USE,
    
    /**
     * Fiber stock is exhausted, no longer available.
     */
    EXHAUSTED,
    
    /**
     * Fiber is obsolete, discontinued, or no longer valid.
     * <p>Can be set from any state when fiber becomes outdated.</p>
     */
    OBSOLETE
}
