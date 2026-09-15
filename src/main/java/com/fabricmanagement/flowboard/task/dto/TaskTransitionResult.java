package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import java.util.UUID;

public record TaskTransitionResult(
    TaskTransitionOutcome outcome,
    String resultType,
    UUID resultId,
    String rejectionCode,
    String rejectionMessage,
    boolean replayed) {}
