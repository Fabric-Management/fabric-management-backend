package com.fabricmanagement.analytics.dto;

import java.util.UUID;

public record MarginWarningDto(String code, UUID referenceId, String message) {}
