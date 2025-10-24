package com.fabricmanagement.shared.domain.policy;

/**
 * Department Type Enum
 * 
 * Functional departments within a company (INTERNAL only).
 */
public enum DepartmentType {
    
    PRODUCTION,    // Üretim
    QUALITY,       // Kalite Kontrol
    WAREHOUSE,     // Depo
    FINANCE,       // Muhasebe
    SALES,         // Satış
    PURCHASING,    // Satın Alma
    HR,            // İnsan Kaynakları
    IT,            // Bilgi İşlem
    MANAGEMENT;    // Yönetim
}
