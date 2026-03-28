package com.fabricmanagement.flowboard.generator.app.adapter;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * TaskTemplateContext: Şablon interpolation işlemleri için event'ten çıkarılan bağlam verisi.
 *
 * <p>Phase 4: Genişletilebilir Event-Template Konfigürasyonu
 */
public record TaskTemplateContext(
    UUID tenantId,
    UUID entityId,
    String entityType,
    String entityRef,
    LocalDate deadline,
    Map<String, String> templateVariables) {}
