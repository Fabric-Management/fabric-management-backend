package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Task yorumu. */
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

  @Column(name = "mentioned_user_ids", columnDefinition = "TEXT")
  private String mentionedUserIds; // JSON array of UUID strings

  public TaskComment(
      UUID tenantId, UUID taskId, UUID userId, String content, String mentionedUserIds) {
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

  public void updateContent(String newContent, String newMentions) {
    this.content = newContent;
    this.mentionedUserIds = newMentions;
  }
}
