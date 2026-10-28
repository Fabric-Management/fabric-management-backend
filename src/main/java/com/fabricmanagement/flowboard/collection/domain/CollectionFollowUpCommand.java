package com.fabricmanagement.flowboard.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(schema = "flowboard", name = "collection_follow_up_command")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionFollowUpCommand extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID followUpId;

  @Column(nullable = false, updatable = false)
  private String fingerprint;

  @Column(updatable = false)
  private UUID contactId;

  @Column(nullable = false, updatable = false)
  private long resultVersion;

  @Column(updatable = false, length = 1000)
  private String reason;

  @Column(updatable = false)
  private UUID responsibleUserId;

  @Override
  protected String getModuleCode() {
    return "CFCMD";
  }

  public static CollectionFollowUpCommand record(
      UUID operation,
      UUID actor,
      UUID followUp,
      String hash,
      UUID contact,
      long version,
      String reason,
      UUID owner) {
    var c = new CollectionFollowUpCommand();
    c.operationId = operation;
    c.actorId = actor;
    c.followUpId = followUp;
    c.fingerprint = hash;
    c.contactId = contact;
    c.resultVersion = version;
    c.reason = reason;
    c.responsibleUserId = owner;
    return c;
  }
}
