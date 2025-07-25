package com.fabricmanagement.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "Email veya telefon numarası boş olamaz")
        String identifier
) {}
