package com.fabricmanagement.company.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Company Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for company service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface CompanyOutboxEventRepository extends OutboxEventRepository {
    
    /**
     * Find company events by company ID
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'COMPANY' 
        AND e.aggregateId = :companyId
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByCompanyId(@Param("companyId") UUID companyId);
    
    /**
     * Find company events by tenant
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'COMPANY' 
        AND e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findCompanyEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}
