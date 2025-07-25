package com.fabricmanagement.user_service.dto.response;

import java.time.LocalDateTime;

public record PasswordChangedResponse(
        boolean success,
        String message,
        LocalDateTime passwordChangedAt,
        boolean shouldRelogin
) {
    public static PasswordChangedResponse success(boolean shouldRelogin) {
        return new PasswordChangedResponse(
                true,
                "Şifreniz başarıyla değiştirildi",
                LocalDateTime.now(),
                shouldRelogin
        );
    }
}
