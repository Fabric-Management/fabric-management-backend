package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsDto;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsRequest;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import com.fabricmanagement.product.fiber.dto.FiberQualityResolutionReason;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardGroupDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant quality profiles targeting a shared ISO code or an exact fibre/mixture (FIBER-CATALOG-1).
 *
 * <p>All reads and writes are tenant-scoped. The target (type, id, captured composition) is fixed
 * at creation; a profile without any criterion is rejected; default selection is serialised per
 * tenant and target and clears the previous default in the same transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberQualityStandardService {

  private final FiberQualityStandardRepository standardRepository;
  private final FiberReferenceQueryService referenceQueryService;
  private final FiberRepository fiberRepository;
  private final FiberQualityQueryService qualityQueryService;

  @Transactional
  public FiberQualityStandardDto create(CreateFiberQualityStandardRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    String name = requireName(request.getStandardName());

    FiberQualityStandard standard =
        switch (request.getTargetType()) {
          case ISO_CODE -> {
            if (request.getFiberId() != null || request.getIsoCodeId() == null) {
              throw targetFieldsInvalid();
            }
            FiberIsoCode isoCode = requireSharedIso(request.getIsoCodeId());
            if (standardRepository
                .existsByTenantIdAndTargetTypeAndIsoCode_IdAndStandardNameAndIsActiveTrue(
                    tenantId, FiberQualityTargetType.ISO_CODE, isoCode.getId(), name)) {
              throw nameExists(name);
            }
            yield FiberQualityStandard.forIsoCode(isoCode, name);
          }
          case FIBER -> {
            if (request.getIsoCodeId() != null || request.getFiberId() == null) {
              throw targetFieldsInvalid();
            }
            Fiber fiber = requireVisibleActiveFiber(tenantId, request.getFiberId());
            if (standardRepository
                .existsByTenantIdAndTargetTypeAndFiberIdAndStandardNameAndIsActiveTrue(
                    tenantId, FiberQualityTargetType.FIBER, fiber.getId(), name)) {
              throw nameExists(name);
            }
            Map<UUID, BigDecimal> captured =
                fiber.isPure()
                    ? FiberComposition.pure(fiber.getId())
                    : FiberComposition.normalize(fiber.getComposition());
            yield FiberQualityStandard.forFiber(fiber.getId(), captured, name);
          }
        };

    copyThresholds(request, standard);
    if (!standard.hasAnyCriterion()) {
      throw emptyProfile();
    }
    validateToleranceRanges(request);

    if (Boolean.TRUE.equals(request.getIsDefault())) {
      clearExistingDefault(tenantId, standard);
      standard.setIsDefault(true);
    }
    FiberQualityStandard saved = saveOrConflict(standard, name);
    log.info(
        "Created fiber quality standard: id={}, target={}, name={}, default={}",
        saved.getId(),
        saved.getTargetType(),
        saved.getStandardName(),
        saved.getIsDefault());
    return FiberQualityStandardDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<FiberQualityStandardDto> getByIsoCodeId(UUID isoCodeId) {
    UUID tenantId = TenantContext.requireTenantId();
    return standardRepository
        .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsActiveTrue(
            tenantId, FiberQualityTargetType.ISO_CODE, isoCodeId)
        .stream()
        .map(FiberQualityStandardDto::from)
        .toList();
  }

  /** Active profiles targeting one visible fibre; another tenant's private fibre is not found. */
  @Transactional(readOnly = true)
  public List<FiberQualityStandardDto> getByFiberId(UUID fiberId) {
    UUID tenantId = TenantContext.requireTenantId();
    requireVisibleFiber(tenantId, fiberId);
    return standardRepository
        .findByTenantIdAndTargetTypeAndFiberIdAndIsActiveTrue(
            tenantId, FiberQualityTargetType.FIBER, fiberId)
        .stream()
        .map(FiberQualityStandardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<FiberQualityStandardDto> getById(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    return standardRepository.findByTenantIdAndId(tenantId, id).map(FiberQualityStandardDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberQualityStandardDto> getAll() {
    UUID tenantId = TenantContext.requireTenantId();
    return standardRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberQualityStandardDto::from)
        .toList();
  }

  /** Profiles grouped by target type + target id, each group with a readable target label. */
  @Transactional(readOnly = true)
  public List<FiberQualityStandardGroupDto> getAllGrouped() {
    UUID tenantId = TenantContext.requireTenantId();
    List<FiberQualityStandard> all = standardRepository.findByTenantIdAndIsActiveTrue(tenantId);
    Map<UUID, Fiber> fibers = targetFibers(tenantId, all);

    Map<String, List<FiberQualityStandard>> byTarget =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    FiberQualityStandardService::groupKey,
                    LinkedHashMap::new,
                    Collectors.toList()));
    return byTarget.values().stream()
        .map(
            profiles -> {
              FiberQualityStandard first = profiles.getFirst();
              return FiberQualityStandardGroupDto.builder()
                  .targetType(first.getTargetType())
                  .isoCodeId(first.getIsoCodeId())
                  .fiberId(first.getFiberId())
                  .targetLabel(targetLabel(first, fibers))
                  .profiles(
                      profiles.stream()
                          .sorted(Comparator.comparing(FiberQualityStandard::getStandardName))
                          .map(FiberQualityStandardDto::from)
                          .toList())
                  .build();
            })
        .sorted(Comparator.comparing(FiberQualityStandardGroupDto::getTargetLabel))
        .toList();
  }

  /**
   * Read-only applicability query with batch-creation rules: every applicable active profile and
   * the default the resolver would choose. Nothing is persisted.
   */
  @Transactional(readOnly = true)
  public FiberApplicableQualityStandardsDto getApplicable(
      FiberApplicableQualityStandardsRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    if (request.composition() != null && request.composition().isEmpty()) {
      throw new FiberDomainException(
          "An explicit empty composition override is invalid", "FIBER_COMPOSITION_EMPTY", 400);
    }
    FiberQualityQueryService.EffectiveComposition effective =
        qualityQueryService.resolveEffectiveComposition(
            tenantId, request.productId(), request.composition());
    List<FiberQualityStandardDto> profiles =
        qualityQueryService.applicableProfiles(tenantId, effective).stream()
            .sorted(Comparator.comparing(FiberQualityStandard::getStandardName))
            .map(FiberQualityStandardDto::from)
            .toList();
    FiberQualityQueryService.QualityResolution resolution =
        qualityQueryService.resolveDefault(tenantId, effective);
    FiberQualityResolutionReason reason =
        switch (resolution.source()) {
          case EXACT_FIBER_DEFAULT -> FiberQualityResolutionReason.EXACT_FIBER_DEFAULT;
          case ISO_DEFAULT -> FiberQualityResolutionReason.ISO_DEFAULT;
          case EXPLICIT, NONE -> FiberQualityResolutionReason.NO_APPLICABLE_DEFAULT;
        };
    return new FiberApplicableQualityStandardsDto(
        profiles, resolution.profile() != null ? resolution.profile().getId() : null, reason);
  }

  @Transactional
  public FiberQualityStandardDto update(
      UUID standardId, UpdateFiberQualityStandardRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    FiberQualityStandard standard = requireOwnActive(tenantId, standardId);
    rejectRetargeting(standard, request);

    String name = requireName(request.getStandardName());
    boolean nameTaken =
        !name.equals(standard.getStandardName())
            && switch (standard.getTargetType()) {
              case ISO_CODE ->
                  standardRepository
                      .existsByTenantIdAndTargetTypeAndIsoCode_IdAndStandardNameAndIsActiveTrue(
                          tenantId, FiberQualityTargetType.ISO_CODE, standard.getIsoCodeId(), name);
              case FIBER ->
                  standardRepository
                      .existsByTenantIdAndTargetTypeAndFiberIdAndStandardNameAndIsActiveTrue(
                          tenantId, FiberQualityTargetType.FIBER, standard.getFiberId(), name);
            };
    if (nameTaken) {
      throw nameExists(name);
    }
    validateToleranceRanges(request);

    standard.setStandardName(name);
    copyThresholds(request, standard);
    if (!standard.hasAnyCriterion()) {
      throw emptyProfile();
    }
    boolean becomesDefault =
        Boolean.TRUE.equals(request.getIsDefault())
            && !Boolean.TRUE.equals(standard.getIsDefault());
    if (becomesDefault) {
      clearExistingDefault(tenantId, standard);
      standard.setIsDefault(true);
    } else if (Boolean.FALSE.equals(request.getIsDefault())) {
      standard.setIsDefault(false);
    }
    FiberQualityStandard saved = saveOrConflict(standard, name);
    log.info(
        "Updated fiber quality standard: id={}, target={}, name={}",
        saved.getId(),
        saved.getTargetType(),
        saved.getStandardName());
    return FiberQualityStandardDto.from(saved);
  }

  @Transactional
  public FiberQualityStandardDto setDefault(UUID standardId) {
    UUID tenantId = TenantContext.requireTenantId();
    FiberQualityStandard standard = requireOwnActive(tenantId, standardId);
    clearExistingDefault(tenantId, standard);
    standard.setIsDefault(true);
    FiberQualityStandard saved = saveOrConflict(standard, standard.getStandardName());
    log.info("Set default quality standard: id={}, target={}", saved.getId(), groupKey(saved));
    return FiberQualityStandardDto.from(saved);
  }

  /**
   * Soft-deletes a quality standard. Returns a warning message if the deleted profile was the
   * default for its target.
   */
  @Transactional
  public String delete(UUID standardId) {
    UUID tenantId = TenantContext.requireTenantId();
    FiberQualityStandard standard = requireOwnActive(tenantId, standardId);
    boolean wasDefault = Boolean.TRUE.equals(standard.getIsDefault());
    standard.setIsDefault(false);
    standard.delete();
    standardRepository.save(standard);
    log.info(
        "Deleted fiber quality standard: id={}, name={}, wasDefault={}",
        standard.getId(),
        standard.getStandardName(),
        wasDefault);
    return wasDefault
        ? "Default standard deleted. Consider setting another profile as default."
        : null;
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private FiberQualityStandard requireOwnActive(UUID tenantId, UUID standardId) {
    return standardRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, standardId)
        .orElseThrow(
            () ->
                new FiberDomainException(
                    "Quality standard not found",
                    "FIBER_QUALITY_STANDARD_NOT_FOUND",
                    404,
                    new Object[] {standardId}));
  }

  private static void rejectRetargeting(
      FiberQualityStandard standard, UpdateFiberQualityStandardRequest request) {
    boolean changed =
        (request.getTargetType() != null && request.getTargetType() != standard.getTargetType())
            || (request.getIsoCodeId() != null
                && !request.getIsoCodeId().equals(standard.getIsoCodeId()))
            || (request.getFiberId() != null
                && !request.getFiberId().equals(standard.getFiberId()));
    if (changed) {
      throw new FiberDomainException(
          "A quality profile's target cannot change; create a new profile instead",
          "FIBER_QUALITY_TARGET_IMMUTABLE",
          409,
          new Object[] {standard.getId()});
    }
  }

  /**
   * Serialises default selection per tenant and target, then clears the previous default in the
   * same transaction (flushed before the new default is written, so the partial unique index never
   * sees two defaults).
   */
  private void clearExistingDefault(UUID tenantId, FiberQualityStandard target) {
    standardRepository.acquireTargetLock("fqs-default|" + tenantId + "|" + groupKey(target));
    Optional<FiberQualityStandard> current =
        switch (target.getTargetType()) {
          case ISO_CODE ->
              standardRepository
                  .findByTenantIdAndTargetTypeAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
                      tenantId, FiberQualityTargetType.ISO_CODE, target.getIsoCodeId());
          case FIBER ->
              standardRepository
                  .findByTenantIdAndTargetTypeAndFiberIdAndIsDefaultTrueAndIsActiveTrue(
                      tenantId, FiberQualityTargetType.FIBER, target.getFiberId());
        };
    current
        .filter(existing -> !Objects.equals(existing.getId(), target.getId()))
        .ifPresent(
            existing -> {
              existing.setIsDefault(false);
              standardRepository.saveAndFlush(existing);
              log.debug("Cleared previous default standard: id={}", existing.getId());
            });
  }

  private FiberQualityStandard saveOrConflict(FiberQualityStandard standard, String name) {
    try {
      return standardRepository.saveAndFlush(standard);
    } catch (DataIntegrityViolationException exception) {
      throw nameExists(name);
    }
  }

  private FiberIsoCode requireSharedIso(UUID isoCodeId) {
    return referenceQueryService
        .findIsoCodeById(isoCodeId)
        .filter(iso -> Boolean.TRUE.equals(iso.getIsActive()))
        .orElseThrow(
            () ->
                new FiberDomainException(
                    "Shared ISO code not found",
                    "FIBER_ISO_NOT_FOUND",
                    404,
                    new Object[] {isoCodeId}));
  }

  private Fiber requireVisibleFiber(UUID tenantId, UUID fiberId) {
    return fiberRepository
        .findByTenantIdInAndId(FiberCatalog.readScope(tenantId), fiberId)
        .orElseThrow(() -> new FiberDomainException("Fiber not found", "FIBER_NOT_FOUND", 404));
  }

  private Fiber requireVisibleActiveFiber(UUID tenantId, UUID fiberId) {
    Fiber fiber = requireVisibleFiber(tenantId, fiberId);
    if (!Boolean.TRUE.equals(fiber.getIsActive())) {
      throw new FiberDomainException("Fiber is inactive", "FIBER_INACTIVE", 409);
    }
    return fiber;
  }

  private Map<UUID, Fiber> targetFibers(UUID tenantId, List<FiberQualityStandard> profiles) {
    Set<UUID> ids = new HashSet<>();
    profiles.stream()
        .filter(profile -> profile.getTargetType() == FiberQualityTargetType.FIBER)
        .forEach(
            profile -> {
              ids.add(profile.getFiberId());
              ids.addAll(profile.getTargetComposition().keySet());
            });
    if (ids.isEmpty()) {
      return Map.of();
    }
    return fiberRepository.findScopedWithReferences(FiberCatalog.readScope(tenantId), ids).stream()
        .collect(Collectors.toMap(Fiber::getId, Function.identity()));
  }

  /** ISO code, or the fibre name with the captured composition's label. */
  private static String targetLabel(FiberQualityStandard profile, Map<UUID, Fiber> fibers) {
    if (profile.getTargetType() == FiberQualityTargetType.ISO_CODE) {
      return profile.getIsoCode() != null ? profile.getIsoCode().getIsoCode() : "";
    }
    Fiber target = fibers.get(profile.getFiberId());
    List<FiberCompositionComponentDto> components =
        profile.getTargetComposition().entrySet().stream()
            .filter(entry -> fibers.containsKey(entry.getKey()))
            .map(
                entry -> {
                  Fiber component = fibers.get(entry.getKey());
                  return new FiberCompositionComponentDto(
                      component.getId(),
                      component.getFiberIsoCodeId(),
                      component.getFiberIsoCode() != null
                          ? component.getFiberIsoCode().getIsoCode()
                          : "",
                      component.getFiberName(),
                      entry.getValue(),
                      component.getMaterialSource());
                })
            .toList();
    String label = FiberCompositionPresenter.label(FiberCompositionPresenter.ordered(components));
    String name = target != null ? target.getFiberName() : profile.getFiberId().toString();
    return label.isEmpty() ? name : name + " (" + label + ")";
  }

  private static String groupKey(FiberQualityStandard profile) {
    return profile.getTargetType()
        + ":"
        + (profile.getTargetType() == FiberQualityTargetType.ISO_CODE
            ? profile.getIsoCodeId()
            : profile.getFiberId());
  }

  private static String requireName(String standardName) {
    if (standardName == null || standardName.isBlank()) {
      throw new FiberDomainException(
          "Standard name is required", "FIBER_QUALITY_STANDARD_NAME_REQUIRED", 400);
    }
    return standardName.trim();
  }

  private static FiberDomainException targetFieldsInvalid() {
    return new FiberDomainException(
        "ISO_CODE needs isoCodeId only; FIBER needs fiberId only",
        "FIBER_QUALITY_TARGET_INVALID",
        400);
  }

  private static FiberDomainException nameExists(String name) {
    return new FiberDomainException(
        String.format("Standard '%s' already exists for this target", name),
        "FIBER_QUALITY_STANDARD_NAME_EXISTS",
        409,
        new Object[] {name});
  }

  private static FiberDomainException emptyProfile() {
    return new FiberDomainException(
        "A quality profile needs at least one threshold; an empty profile would approve every"
            + " measurement",
        "FIBER_QUALITY_STANDARD_EMPTY",
        400);
  }

  private static void copyThresholds(
      CreateFiberQualityStandardRequest req, FiberQualityStandard standard) {
    standard.setFinenessMin(req.getFinenessMin());
    standard.setFinenessTarget(req.getFinenessTarget());
    standard.setFinenessMax(req.getFinenessMax());
    standard.setLengthMmMin(req.getLengthMmMin());
    standard.setLengthMmTarget(req.getLengthMmTarget());
    standard.setLengthMmMax(req.getLengthMmMax());
    standard.setStrengthCndTexMin(req.getStrengthCndTexMin());
    standard.setStrengthCndTexTarget(req.getStrengthCndTexTarget());
    standard.setStrengthCndTexMax(req.getStrengthCndTexMax());
    standard.setElongationPctMin(req.getElongationPctMin());
    standard.setElongationPctTarget(req.getElongationPctTarget());
    standard.setElongationPctMax(req.getElongationPctMax());
    standard.setMoisturePctMin(req.getMoisturePctMin());
    standard.setMoisturePctTarget(req.getMoisturePctTarget());
    standard.setMoisturePctMax(req.getMoisturePctMax());
    standard.setTrashContentPctMin(req.getTrashContentPctMin());
    standard.setTrashContentPctTarget(req.getTrashContentPctTarget());
    standard.setTrashContentPctMax(req.getTrashContentPctMax());
    standard.setUniformityIndexMin(req.getUniformityIndexMin());
    standard.setUniformityIndexTarget(req.getUniformityIndexTarget());
    standard.setUniformityIndexMax(req.getUniformityIndexMax());
  }

  private static void copyThresholds(
      UpdateFiberQualityStandardRequest req, FiberQualityStandard standard) {
    standard.setFinenessMin(req.getFinenessMin());
    standard.setFinenessTarget(req.getFinenessTarget());
    standard.setFinenessMax(req.getFinenessMax());
    standard.setLengthMmMin(req.getLengthMmMin());
    standard.setLengthMmTarget(req.getLengthMmTarget());
    standard.setLengthMmMax(req.getLengthMmMax());
    standard.setStrengthCndTexMin(req.getStrengthCndTexMin());
    standard.setStrengthCndTexTarget(req.getStrengthCndTexTarget());
    standard.setStrengthCndTexMax(req.getStrengthCndTexMax());
    standard.setElongationPctMin(req.getElongationPctMin());
    standard.setElongationPctTarget(req.getElongationPctTarget());
    standard.setElongationPctMax(req.getElongationPctMax());
    standard.setMoisturePctMin(req.getMoisturePctMin());
    standard.setMoisturePctTarget(req.getMoisturePctTarget());
    standard.setMoisturePctMax(req.getMoisturePctMax());
    standard.setTrashContentPctMin(req.getTrashContentPctMin());
    standard.setTrashContentPctTarget(req.getTrashContentPctTarget());
    standard.setTrashContentPctMax(req.getTrashContentPctMax());
    standard.setUniformityIndexMin(req.getUniformityIndexMin());
    standard.setUniformityIndexTarget(req.getUniformityIndexTarget());
    standard.setUniformityIndexMax(req.getUniformityIndexMax());
  }

  private void validateToleranceRanges(CreateFiberQualityStandardRequest req) {
    validateRange("Fineness", req.getFinenessMin(), req.getFinenessTarget(), req.getFinenessMax());
    validateRange("Length mm", req.getLengthMmMin(), req.getLengthMmTarget(), req.getLengthMmMax());
    validateRange(
        "Strength",
        req.getStrengthCndTexMin(),
        req.getStrengthCndTexTarget(),
        req.getStrengthCndTexMax());
    validateRange(
        "Elongation",
        req.getElongationPctMin(),
        req.getElongationPctTarget(),
        req.getElongationPctMax());
    validateRange(
        "Moisture", req.getMoisturePctMin(), req.getMoisturePctTarget(), req.getMoisturePctMax());
    validateRange(
        "Trash content",
        req.getTrashContentPctMin(),
        req.getTrashContentPctTarget(),
        req.getTrashContentPctMax());
    validateRange(
        "Uniformity index",
        req.getUniformityIndexMin(),
        req.getUniformityIndexTarget(),
        req.getUniformityIndexMax());
  }

  private void validateToleranceRanges(UpdateFiberQualityStandardRequest req) {
    validateRange("Fineness", req.getFinenessMin(), req.getFinenessTarget(), req.getFinenessMax());
    validateRange("Length mm", req.getLengthMmMin(), req.getLengthMmTarget(), req.getLengthMmMax());
    validateRange(
        "Strength",
        req.getStrengthCndTexMin(),
        req.getStrengthCndTexTarget(),
        req.getStrengthCndTexMax());
    validateRange(
        "Elongation",
        req.getElongationPctMin(),
        req.getElongationPctTarget(),
        req.getElongationPctMax());
    validateRange(
        "Moisture", req.getMoisturePctMin(), req.getMoisturePctTarget(), req.getMoisturePctMax());
    validateRange(
        "Trash content",
        req.getTrashContentPctMin(),
        req.getTrashContentPctTarget(),
        req.getTrashContentPctMax());
    validateRange(
        "Uniformity index",
        req.getUniformityIndexMin(),
        req.getUniformityIndexTarget(),
        req.getUniformityIndexMax());
  }

  private void validateRange(String param, Double min, Double target, Double max) {
    if (min != null && max != null && min > max) {
      throw new FiberDomainException(
          String.format("%s: min (%.2f) cannot exceed max (%.2f)", param, min, max));
    }
    if (target != null) {
      if (min != null && target < min) {
        throw new FiberDomainException(
            String.format("%s: target (%.2f) cannot be below min (%.2f)", param, target, min));
      }
      if (max != null && target > max) {
        throw new FiberDomainException(
            String.format("%s: target (%.2f) cannot exceed max (%.2f)", param, target, max));
      }
    }
  }
}
