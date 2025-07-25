package com.fabricmanagement.user_service.dto.response;

import java.time.LocalDateTime;

public record EmailVerificationResponse(
        boolean success,
        String message,
        String email,
        LocalDateTime verifiedAt
) {
    public static EmailVerificationResponse success(String email) {
        return new EmailVerificationResponse(
                true,
                "Email adresiniz başarıyla doğrulandı",
                email,
                LocalDateTime.now()
        );
    }
}
