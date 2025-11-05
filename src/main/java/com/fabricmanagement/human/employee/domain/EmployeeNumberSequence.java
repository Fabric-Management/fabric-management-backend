package com.fabricmanagement.human.employee.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Employee Number Sequence - Tracks auto-incrementing employee numbers per tenant.
 * 
 * <p><b>Purpose:</b> Optimize employee number generation by avoiding full table scans.</p>
 * 
 * <p><b>Format:</b> {TENANT_UID}-EMP-{SEQUENCE}</p>
 * <p><b>Example:</b> "ACME-001-EMP-00042"</p>
 * 
 * <p><b>Design Decision:</b> Global sequence (not year-based) to avoid:
 * <ul>
 *   <li>❌ Sequence reset issues at year boundary</li>
 *   <li>❌ Duplicate sequence numbers across years</li>
 *   <li>❌ Sequence exhaustion in large companies (10,000+ employees/year)</li>
 * </ul>
 * 
 * <p><b>Year Information:</b> Available from Employee.hireDate if needed.</p>
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Single primary key: tenant_id (one sequence per tenant)</li>
 *   <li>Atomic sequence increment using SELECT FOR UPDATE</li>
 *   <li>No race conditions - database-level locking</li>
 *   <li>Efficient - single query instead of full table scan</li>
 *   <li>Never resets - globally unique within tenant</li>
 * </ul>
 */
@Entity
@Table(name = "human_employee_number_sequence", schema = "human",
    indexes = {
        @Index(name = "idx_emp_seq_tenant", columnList = "tenant_id", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeNumberSequence {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "next_sequence", nullable = false)
    @Builder.Default
    private Integer nextSequence = 1;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Increment sequence and return next value.
     * 
     * @return Next sequence number
     */
    public Integer getNextAndIncrement() {
        Integer current = this.nextSequence;
        this.nextSequence = current + 1;
        this.updatedAt = Instant.now();
        return current;
    }

    /**
     * Create new sequence for tenant.
     */
    public static EmployeeNumberSequence create(UUID tenantId) {
        return EmployeeNumberSequence.builder()
            .tenantId(tenantId)
            .nextSequence(1)
            .updatedAt(Instant.now())
            .build();
    }
}

