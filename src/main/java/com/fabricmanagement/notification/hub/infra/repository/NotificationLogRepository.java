package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The log holds one row per delivered channel: an event with an in-app and an e-mail template
 * leaves two rows. The notification centre shows the in-app rows only; the others are delivery
 * history. Every read the centre makes therefore names its channel, so an e-mail row is never
 * listed or counted as a second notification.
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  @Query(
      """
      SELECT nl FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId
        AND nl.channel = :channel
        AND nl.isActive = true
      """)
  Page<NotificationLog> findByRecipientAndChannel(
      @Param("recipientId") UUID recipientId,
      @Param("channel") NotificationChannel channel,
      Pageable pageable);

  @Query(
      """
      SELECT nl FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId
        AND nl.channel = :channel
        AND nl.isRead = false
        AND nl.isActive = true
      """)
  Page<NotificationLog> findUnreadByRecipientAndChannel(
      @Param("recipientId") UUID recipientId,
      @Param("channel") NotificationChannel channel,
      Pageable pageable);

  @Query(
      """
      SELECT COUNT(nl) FROM NotificationLog nl
      WHERE nl.recipientId = :recipientId
        AND nl.channel = :channel
        AND nl.isRead = false
        AND nl.isActive = true
      """)
  long countUnreadByRecipientAndChannel(
      @Param("recipientId") UUID recipientId, @Param("channel") NotificationChannel channel);

  @Modifying
  @Query(
      """
      UPDATE NotificationLog nl
      SET nl.isRead = true, nl.readAt = CURRENT_TIMESTAMP
      WHERE nl.recipientId = :recipientId
        AND nl.channel = :channel
        AND nl.isRead = false
      """)
  int markAllReadForRecipientAndChannel(
      @Param("recipientId") UUID recipientId, @Param("channel") NotificationChannel channel);
}
