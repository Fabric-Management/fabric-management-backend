package com.fabricmanagement.user_service.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    USER("Standart Kullanıcı"),
    ADMIN("Sistem Yöneticisi"),
    EMPLOYEE("Çalışan"),
    MANAGER("Yönetici"),
    COMPANY_OWNER("Firma Sahibi"),
    COMPANY_MANAGER("Firma Yöneticisi"),
    CUSTOMER("Müşteri");

    private final String description;
}