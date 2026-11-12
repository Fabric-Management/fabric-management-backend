package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationQueue;
import com.fabricmanagement.notification.hub.domain.NotificationQueueStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT q FROM NotificationQueue q WHERE q.id = :id")
  Optional<NotificationQueue> findByIdWithWriteLock(@Param("id") UUID id);

  @Query(
      """
      SELECT COUNT(q) FROM NotificationQueue q
      WHERE q.recipientId = :recipientId
        AND q.status = :status
      """)
  long countPendingForRecipient(
      @Param("recipientId") UUID recipientId, @Param("status") NotificationQueueStatus status);
}
