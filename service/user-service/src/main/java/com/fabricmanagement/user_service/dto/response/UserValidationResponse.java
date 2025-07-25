package com.fabricmanagement.user_service.dto.response;

public record UserValidationResponse(
        boolean exists,
        boolean isActive,
        boolean canLogin,
        String message
) {
    public static UserValidationResponse userNotFound() {
        return new UserValidationResponse(
                false,
                false,
                false,
                "Kullanıcı bulunamadı"
        );
    }

    public static UserValidationResponse valid() {
        return new UserValidationResponse(
                true,
                true,
                true,
                "Kullanıcı aktif ve giriş yapabilir"
        );
    }

    public static UserValidationResponse inactive(String reason) {
        return new UserValidationResponse(
                true,
                false,
                false,
                reason
        );
    }
}
