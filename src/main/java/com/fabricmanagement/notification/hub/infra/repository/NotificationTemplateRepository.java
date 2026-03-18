package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

  @Query(
      """
      SELECT t FROM NotificationTemplate t
      WHERE t.eventType = :eventType
        AND t.channel = :channel
        AND t.isActive = true
      """)
  Optional<NotificationTemplate> findByEventTypeAndChannel(
      @Param("eventType") String eventType, @Param("channel") NotificationChannel channel);

  @Query(
      """
      SELECT t FROM NotificationTemplate t
      WHERE t.eventType = :eventType AND t.isActive = true
      """)
  List<NotificationTemplate> findAllByEventType(@Param("eventType") String eventType);
}
