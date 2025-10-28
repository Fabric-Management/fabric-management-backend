package com.fabricmanagement.production.execution.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FiberBatch represents a physical production lot/batch of fiber.
 *
 * <p>A FiberBatch is created when fiber is received from a supplier or produced internally.
 * It tracks the actual inventory of fiber that can be used in production.
 *
 * <p>Key Concepts:
 * <ul>
 *   <li>Links to Fiber (masterdata) via fiberId</li>
 *   <li>Has a unique batchCode for traceability</li>
 *   <li>Tracks quantity and supplierBatch information</li>
 *   <li>Supports status tracking (NEW, IN_USE, DEPLETED)</li>
 * </ul>
 */
@Entity
@Table(name = "production_execution_fiber_batch", schema = "production")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberBatch extends BaseEntity {

    @Column(name = "fiber_id", nullable = false)
    private UUID fiberId;
    
    @Column(name = "batch_code", nullable = false, unique = true)
    private String batchCode;
    
    @Column(name = "supplier_batch_code")
    private String supplierBatchCode;
    
    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
    
    @Column(name = "unit", nullable = false)
    private String unit;
    
    @Column(name = "production_date")
    private Instant productionDate;
    
    @Column(name = "expiry_date")
    private Instant expiryDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FiberBatchStatus status;
    
    @Column(name = "warehouse_location")
    private String warehouseLocation;
    
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Create a new fiber batch.
     */
    public static FiberBatch create(UUID tenantId, UUID fiberId, String batchCode, 
                                   String supplierBatchCode, BigDecimal quantity, String unit,
                                   Instant productionDate, Instant expiryDate, String warehouseLocation, String remarks) {
        
        FiberBatch batch = new FiberBatch();
        batch.setTenantId(tenantId);
        batch.setUid(generateUid(batchCode));
        batch.setFiberId(fiberId);
        batch.setBatchCode(batchCode);
        batch.setSupplierBatchCode(supplierBatchCode);
        batch.setQuantity(quantity);
        batch.setUnit(unit);
        batch.setProductionDate(productionDate);
        batch.setExpiryDate(expiryDate);
        batch.setStatus(FiberBatchStatus.NEW);
        batch.setWarehouseLocation(warehouseLocation);
        batch.setRemarks(remarks);
        batch.onCreate();
        
        return batch;
    }

    /**
     * Mark batch as in use.
     */
    public void markInUse() {
        if (this.status == FiberBatchStatus.NEW || this.status == FiberBatchStatus.RESERVED) {
            this.status = FiberBatchStatus.IN_USE;
            onUpdate();
        } else {
            throw new IllegalStateException("Batch must be in NEW or RESERVED status to mark as IN_USE");
        }
    }

    /**
     * Mark batch as depleted.
     */
    public void markDepleted() {
        if (this.status == FiberBatchStatus.IN_USE) {
            this.status = FiberBatchStatus.DEPLETED;
            onUpdate();
        } else {
            throw new IllegalStateException("Batch must be in IN_USE status to mark as DEPLETED");
        }
    }

    /**
     * Deduct quantity from batch.
     */
    public void deduct(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deduction amount must be positive");
        }
        if (amount.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("Insufficient quantity in batch");
        }
        this.quantity = this.quantity.subtract(amount);
        onUpdate();
        
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            markDepleted();
        }
    }

    private static String generateUid(String batchCode) {
        return "FIBER-BATCH-" + batchCode;
    }
    
    /**
     * Get module code for UID generation.
     */
    @Override
    protected String getModuleCode() {
        return "EXEC-FB";
    }
}

