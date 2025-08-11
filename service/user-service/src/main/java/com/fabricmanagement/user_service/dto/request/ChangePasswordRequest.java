package com.fabricmanagement.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut şifre boş olamaz")
        String currentPassword,

        @NotBlank(message = "Yeni şifre boş olamaz")
        @Size(min = 8, max = 100, message = "Şifre 8-100 karakter arasında olmalıdır")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Şifre en az bir küçük harf, bir büyük harf ve bir rakam içermelidir"
        )
        String newPassword
) {}
