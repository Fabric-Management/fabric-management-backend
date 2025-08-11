package com.fabricmanagement.user_service.dto.response;

import com.fabricmanagement.user_service.util.DateTimeHelper;
import java.time.LocalDateTime;

public record PasswordCreatedResponse(
        boolean success,
        String message,
        LocalDateTime passwordChangedAt
) {
    /**
     * Creates a successful password creation response
     */
    public static PasswordCreatedResponse ofSuccess() {
        return new PasswordCreatedResponse(
                true,
                "Şifreniz başarıyla oluşturuldu",
                DateTimeHelper.now()
        );
    }

    /**
     * Creates a failed password creation response with custom message
     */
    public static PasswordCreatedResponse ofFailure(String message) {
        return new PasswordCreatedResponse(
                false,
                message,
                null
        );
    }

    /**
     * Creates a custom password creation response
     */
    public static PasswordCreatedResponse of(boolean success, String message) {
        return new PasswordCreatedResponse(
                success,
                message,
                success ? DateTimeHelper.now() : null
        );
    }
}