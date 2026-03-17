package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationQueue;
import com.fabricmanagement.notification.hub.domain.NotificationQueueStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, UUID> {

  @Query(
      """
      SELECT q FROM NotificationQueue q
      WHERE q.status = :status
        AND q.retryCount < 3
        AND q.isActive = true
      ORDER BY q.createdAt ASC
      """)
  List<NotificationQueue> findPendingForProcessing(
      @Param("status") NotificationQueueStatus status, Pageable pageable);

  @Query(
      """
      SELECT q FROM NotificationQueue q
      WHERE q.status = 'PROCESSING'
        AND q.updatedAt < :threshold
        AND q.isActive = true
      """)
  List<NotificationQueue> findStuckProcessing(@Param("threshold") Instant threshold);

  @Query(
      """
      SELECT COUNT(q) FROM NotificationQueue q
      WHERE q.recipientId = :recipientId
        AND q.status = :status
      """)
  long countPendingForRecipient(
      @Param("recipientId") UUID recipientId, @Param("status") NotificationQueueStatus status);
}
