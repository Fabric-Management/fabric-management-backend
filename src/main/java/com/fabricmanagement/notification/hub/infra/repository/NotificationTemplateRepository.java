package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Every lookup names the tenant. Relying on row-level security alone returns one tenant's rows in
 * production and every tenant's rows on a connection that bypasses it, so the same call would
 * deliver once or once per tenant.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

  Optional<NotificationTemplate> findByTenantIdAndEventTypeAndChannelAndIsActiveTrue(
      UUID tenantId, String eventType, NotificationChannel channel);

  List<NotificationTemplate> findAllByTenantIdAndEventTypeAndIsActiveTrue(
      UUID tenantId, String eventType);

  List<NotificationTemplate> findAllByTenantIdAndChannelAndIsActiveTrue(
      UUID tenantId, NotificationChannel channel);
}
