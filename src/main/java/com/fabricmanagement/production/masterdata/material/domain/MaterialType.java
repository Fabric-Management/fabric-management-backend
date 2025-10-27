package com.fabricmanagement.production.masterdata.material.domain;

/**
 * Material types in textile production.
 *
 * <p>Categories:</p>
 * <ul>
 *   <li>FIBER - Raw fibers (cotton, polyester, wool)</li>
 *   <li>YARN - Spun yarns</li>
 *   <li>FABRIC - Woven or knitted fabrics</li>
 *   <li>CHEMICAL - Dyes, auxiliaries</li>
 *   <li>CONSUMABLE - Oils, needles, machine parts</li>
 * </ul>
 */
public enum MaterialType {
    
    /**
     * Raw fiber - Cotton, polyester, wool, etc.
     */
    FIBER,
    
    /**
     * Yarn - Spun from fibers.
     */
    YARN,
    
    /**
     * Fabric - Woven or knitted fabric.
     */
    FABRIC,
    
    /**
     * Chemical - Dyes, auxiliaries, finishing chemicals.
     */
    CHEMICAL,
    
    /**
     * Consumable - Oils, needles, machine parts, cleaning supplies.
     */
    CONSUMABLE
}

