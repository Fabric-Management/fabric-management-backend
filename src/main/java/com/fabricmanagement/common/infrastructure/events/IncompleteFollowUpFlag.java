package com.fabricmanagement.common.infrastructure.events;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "common_infrastructure", name = "incomplete_follow_up_flag")
@Getter
@NoArgsConstructor
public class IncompleteFollowUpFlag extends BaseEntity {

  @Column(name = "publication_id", nullable = false)
  private UUID publicationId;

  @Column(name = "event_type", nullable = false, length = 512)
  private String eventType;

  @Column(name = "entity_type", nullable = false, length = 64)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @Column(name = "entity_ref", length = 128)
  private String entityRef;

  @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
  private String summary;

  @Column(name = "reference_type", length = 64)
  private String referenceType;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "affected_user_id")
  private UUID affectedUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FollowUpFlagStatus status;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public static IncompleteFollowUpFlag raise(
      UUID tenantId, UUID publicationId, String eventType, StuckEventPresentation presentation) {
    IncompleteFollowUpFlag flag = new IncompleteFollowUpFlag();
    flag.setTenantId(tenantId);
    flag.publicationId = publicationId;
    flag.eventType = eventType;
    flag.apply(presentation);
    flag.status = FollowUpFlagStatus.ACTIVE;
    return flag;
  }

  public void raiseAgain(StuckEventPresentation presentation) {
    apply(presentation);
    status = FollowUpFlagStatus.ACTIVE;
    resolvedAt = null;
  }

  public void resolve(Instant resolvedAt) {
    if (status == FollowUpFlagStatus.RESOLVED) {
      return;
    }
    status = FollowUpFlagStatus.RESOLVED;
    this.resolvedAt = resolvedAt;
  }

  private void apply(StuckEventPresentation presentation) {
    entityType = presentation.entityType();
    entityId = presentation.entityId();
    entityRef = presentation.entityRef();
    summary = presentation.summary();
    referenceType = presentation.referenceType();
    referenceId = presentation.referenceId();
    affectedUserId = presentation.affectedUserId();
  }

  @Override
  public String getModuleCode() {
    return "IFUF";
  }
}
