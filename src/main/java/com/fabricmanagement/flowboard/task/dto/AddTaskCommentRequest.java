package com.fabricmanagement.flowboard.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AddTaskCommentRequest(
    @NotBlank @Size(max = 20000) String content, List<UUID> mentionedUserIds) {}
