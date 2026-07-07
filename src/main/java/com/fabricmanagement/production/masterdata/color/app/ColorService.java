package com.fabricmanagement.production.masterdata.color.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ColorService {

  private final ColorRepository colorRepository;

  @Transactional(readOnly = true)
  public List<Color> list(boolean includeInactive) {
    UUID tenantId = TenantContext.requireTenantId();
    return includeInactive
        ? colorRepository.findByTenantIdOrderByCode(tenantId)
        : colorRepository.findByTenantIdAndIsActiveTrueOrderByCode(tenantId);
  }

  @Transactional(readOnly = true)
  public Color findById(UUID colorId) {
    UUID tenantId = TenantContext.requireTenantId();
    return colorRepository
        .findByTenantIdAndId(tenantId, colorId)
        .orElseThrow(() -> new NotFoundException("Color not found: " + colorId));
  }

  @Transactional
  public Color create(String code, String name, String colorHex) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedCode = normalizeCode(code);
    if (colorRepository.existsByTenantIdAndCode(tenantId, normalizedCode)) {
      throw ColorDomainException.duplicateCode(normalizedCode);
    }

    return colorRepository.save(Color.create(tenantId, normalizedCode, name, colorHex));
  }

  @Transactional
  public Color update(UUID colorId, String code, String name, String colorHex) {
    UUID tenantId = TenantContext.requireTenantId();
    Color color =
        colorRepository
            .findByTenantIdAndId(tenantId, colorId)
            .orElseThrow(() -> new NotFoundException("Color not found: " + colorId));

    String normalizedCode = normalizeCode(code);
    colorRepository
        .findByTenantIdAndCode(tenantId, normalizedCode)
        .filter(existing -> !existing.getId().equals(colorId))
        .ifPresent(
            existing -> {
              throw ColorDomainException.duplicateCode(normalizedCode);
            });

    color.update(normalizedCode, name, colorHex);
    return colorRepository.save(color);
  }

  @Transactional
  public void deactivate(UUID colorId) {
    Color color = findById(colorId);
    color.delete();
    colorRepository.save(color);
    log.info("Color deactivated: id={}, code={}", colorId, color.getCode());
  }

  private String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Color code must not be blank");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }
}
