package com.fabricmanagement.flowboard.dashboard.dto;

import java.util.UUID;

public record AssigneeWorkloadDto(UUID userId, String fullName, int taskCount) {}
