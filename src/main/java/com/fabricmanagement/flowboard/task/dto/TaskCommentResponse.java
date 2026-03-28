package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskComment;
import com.fabricmanagement.platform.user.dto.UserDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Task yorumu API yanıtı.
 *
 * <p>Entity'de yazar adı yok; {@code createdByName} UserFacade ile doldurulur (yoksa {@code null}).
 */
public record TaskCommentResponse(
    UUID id,
    UUID taskId,
    UUID userId,
    String content,
    List<UUID> mentionedUserIds,
    Instant createdAt,
    String createdByName) {

  public static TaskCommentResponse from(TaskComment comment, UserDto author) {
    List<UUID> mentions =
        comment.getMentionedUserIds() != null ? comment.getMentionedUserIds() : List.of();
    return new TaskCommentResponse(
        comment.getId(),
        comment.getTaskId(),
        comment.getUserId(),
        comment.getContent(),
        mentions,
        comment.getCreatedAt(),
        author != null ? displayName(author) : null);
  }

  private static String displayName(UserDto u) {
    if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) {
      return u.getDisplayName();
    }
    String first = u.getFirstName() != null ? u.getFirstName() : "";
    String last = u.getLastName() != null ? u.getLastName() : "";
    String combined = (first + " " + last).trim();
    return combined.isEmpty() ? null : combined;
  }
}
