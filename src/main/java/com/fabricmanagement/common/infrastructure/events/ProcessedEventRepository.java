package com.fabricmanagement.common.infrastructure.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository
    extends JpaRepository<ProcessedEventEntry, ProcessedEventEntry.ProcessedEventId> {

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_event (event_id, listener_id, processed_at) VALUES (:eventId, :listenerId, now()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int tryInsert(@Param("eventId") UUID eventId, @Param("listenerId") String listenerId);

  @Modifying
  @Query("DELETE FROM ProcessedEventEntry p WHERE p.processedAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
