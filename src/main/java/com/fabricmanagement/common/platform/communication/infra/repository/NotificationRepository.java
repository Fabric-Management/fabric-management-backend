package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /** Unread notifications for a tenant, newest first */
  List<Notification> findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(UUID tenantId);

  /** Unread notifications for a recipient, newest first */
  List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UUID recipientId);

  /** Paginated notifications for a tenant, newest first */
  Page<Notification> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  /** Count of unread notifications for a recipient */
  long countByRecipientIdAndIsReadFalse(UUID recipientId);

  /** Paginated notifications for a recipient, newest first */
  Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

  /** Paginated unread notifications for a recipient, newest first */
  Page<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
      UUID recipientId, Pageable pageable);
}
