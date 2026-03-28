package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(schema = "flowboard", name = "task_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskComment extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Type(JsonType.class)
  @Column(name = "mentioned_user_ids", columnDefinition = "jsonb")
  private List<UUID> mentionedUserIds;

  public TaskComment(
      UUID tenantId, UUID taskId, UUID userId, String content, List<UUID> mentionedUserIds) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.userId = userId;
    this.content = content;
    this.mentionedUserIds = mentionedUserIds;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  public void updateContent(String newContent, List<UUID> newMentions) {
    this.content = newContent;
    this.mentionedUserIds = newMentions;
  }
}
