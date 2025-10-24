package com.fabricmanagement.auth.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.stereotype.Repository;

/**
 * Auth Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for auth service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface AuthOutboxEventRepository extends OutboxEventRepository {
    
    // Inherits all methods from OutboxEventRepository
    // Service-specific queries can be added here if needed
}
