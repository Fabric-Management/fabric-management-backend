package com.fabricmanagement.user_service.dto.response;

import java.util.UUID;

public record UserMinimalResponse(
        UUID id,
        String username,
        String displayName,
        String initials
) {}
