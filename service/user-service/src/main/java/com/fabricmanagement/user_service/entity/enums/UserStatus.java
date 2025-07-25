package com.fabricmanagement.user_service.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("Aktif"),
    INACTIVE("Pasif"),
    SUSPENDED("Askıya Alınmış"),
    PENDING_VERIFICATION("Doğrulama Bekliyor"),
    LOCKED("Kilitli");

    private final String description;
}