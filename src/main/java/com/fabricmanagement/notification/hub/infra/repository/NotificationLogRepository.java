package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  @Query(
      """
      SELECT nl FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId
        AND nl.isRead = false
        AND nl.isActive = true
      ORDER BY nl.sentAt DESC
      """)
  List<NotificationLog> findUnreadByRecipient(@Param("recipientId") UUID recipientId);

  @Query(
      """
      SELECT nl FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId
        AND nl.isActive = true
      ORDER BY nl.sentAt DESC
      """)
  Page<NotificationLog> findByRecipient(@Param("recipientId") UUID recipientId, Pageable pageable);

  @Query(
      """
      SELECT COUNT(nl) FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId AND nl.isRead = false AND nl.isActive = true
      """)
  long countUnreadByRecipient(@Param("recipientId") UUID recipientId);

  @Modifying
  @Query(
      """
      UPDATE NotificationLog nl
      SET nl.isRead = true, nl.readAt = CURRENT_TIMESTAMP
      WHERE nl.recipientId = :recipientId AND nl.isRead = false
      """)
  int markAllReadForRecipient(@Param("recipientId") UUID recipientId);
}
