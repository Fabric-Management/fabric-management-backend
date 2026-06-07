package com.fabricmanagement.common.infrastructure.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEventEntry.ProcessedEventId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProcessedEventEntry {
  @Id private UUID eventId;

  @Id
  @Column(length = 255)
  private String listenerId;

  private Instant processedAt;

  public ProcessedEventEntry(UUID eventId, String listenerId, Instant processedAt) {
    this.eventId = eventId;
    this.listenerId = listenerId;
    this.processedAt = processedAt;
  }

  public record ProcessedEventId(UUID eventId, String listenerId) implements Serializable {}
}
