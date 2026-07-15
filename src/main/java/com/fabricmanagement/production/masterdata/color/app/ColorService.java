package com.fabricmanagement.production.masterdata.color.app;

import com.fabricmanagement.common.infrastructure.persistence.LikePattern;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ColorService {

  private final ColorRepository colorRepository;

  @Transactional(readOnly = true)
  public Page<Color> list(
      String q,
      ColorType colorType,
      ColorFamily colorFamily,
      ColorStandardStatus standardStatus,
      boolean includeInactive,
      Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();

    Specification<Color> specification =
        (root, query, criteriaBuilder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));

          if (!includeInactive) {
            predicates.add(criteriaBuilder.isTrue(root.get("isActive")));
          }
          if (q != null && !q.isBlank()) {
            String pattern = LikePattern.literalContains(q.trim().toLowerCase(Locale.ROOT));
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("code")),
                        pattern,
                        LikePattern.ESCAPE_CHARACTER),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("name")),
                        pattern,
                        LikePattern.ESCAPE_CHARACTER)));
          }
          if (colorType != null) {
            predicates.add(criteriaBuilder.equal(root.get("colorType"), colorType));
          }
          if (colorFamily != null) {
            predicates.add(criteriaBuilder.equal(root.get("colorFamily"), colorFamily));
          }
          if (standardStatus != null) {
            predicates.add(criteriaBuilder.equal(root.get("standardStatus"), standardStatus));
          }

          return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

    return colorRepository.findAll(specification, pageable);
  }

  /** Internal convenience for seeders and query ports that still require an unpaged snapshot. */
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

  /** Convenience for seeders and callers that only need the three original fields. */
  @Transactional
  public Color create(String code, String name, String colorHex) {
    return create(ColorCardSpec.basic(code, name, colorHex));
  }

  @Transactional
  public Color create(ColorCardSpec spec) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedCode = normalizeCode(spec.code());
    if (colorRepository.existsByTenantIdAndCode(tenantId, normalizedCode)) {
      throw ColorDomainException.duplicateCode(normalizedCode);
    }

    return colorRepository.save(Color.create(tenantId, spec));
  }

  @Transactional
  public Color update(UUID colorId, ColorCardSpec spec) {
    UUID tenantId = TenantContext.requireTenantId();
    Color color =
        colorRepository
            .findByTenantIdAndId(tenantId, colorId)
            .orElseThrow(() -> new NotFoundException("Color not found: " + colorId));

    String normalizedCode = normalizeCode(spec.code());
    colorRepository
        .findByTenantIdAndCode(tenantId, normalizedCode)
        .filter(existing -> !existing.getId().equals(colorId))
        .ifPresent(
            existing -> {
              throw ColorDomainException.duplicateCode(normalizedCode);
            });

    color.update(spec);
    return colorRepository.save(color);
  }

  /** Soft-deletes the card. Idempotent: deactivating an inactive card is a no-op re-save. */
  @Transactional
  public Color deactivate(UUID colorId) {
    Color color = findById(colorId);
    color.delete();
    log.info("Color deactivated: id={}, code={}", colorId, color.getCode());
    return colorRepository.save(color);
  }

  /**
   * Restores a soft-deleted card. Counterpart of {@link #deactivate(UUID)} — without it a
   * deactivated code stays permanently unusable, since {@link #create} rejects duplicate codes
   * across active and inactive rows alike. Idempotent.
   */
  @Transactional
  public Color activate(UUID colorId) {
    Color color = findById(colorId);
    color.activate();
    log.info("Color activated: id={}, code={}", colorId, color.getCode());
    return colorRepository.save(color);
  }

  /** Freezes the card's shade standard. Idempotent. */
  @Transactional
  public Color approve(UUID colorId) {
    Color color = findById(colorId);
    color.approve();
    log.info("Color standard approved: id={}, code={}", colorId, color.getCode());
    return colorRepository.save(color);
  }

  /** Reopens the card's shade standard for editing. Idempotent. */
  @Transactional
  public Color revertToDraft(UUID colorId) {
    Color color = findById(colorId);
    color.revertToDraft();
    log.info("Color standard reverted to draft: id={}, code={}", colorId, color.getCode());
    return colorRepository.save(color);
  }

  private String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Color code must not be blank");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }
}
