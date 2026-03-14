package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberQualityStandardGroupDto;
import com.fabricmanagement.production.masterdata.fiber.dto.UpdateFiberQualityStandardRequest;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberQualityStandardRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiberQualityStandardService {

  private final FiberQualityStandardRepository standardRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;

  @Transactional
  public FiberQualityStandardDto create(CreateFiberQualityStandardRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating fiber quality standard: tenantId={}, isoCodeId={}, standardName={}",
        tenantId,
        request.getIsoCodeId(),
        request.getStandardName());

    FiberIsoCode isoCode =
        fiberIsoCodeRepository
            .findById(request.getIsoCodeId())
            .orElseThrow(
                () -> new NotFoundException("ISO code not found: " + request.getIsoCodeId()));

    if (standardRepository.existsByTenantIdAndIsoCode_IdAndStandardName(
        tenantId, request.getIsoCodeId(), request.getStandardName())) {
      throw new FiberDomainException(
          String.format(
              "Standard '%s' already exists for this ISO code", request.getStandardName()));
    }

    validateToleranceRanges(request);

    boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());

    if (makeDefault) {
      clearExistingDefault(tenantId, request.getIsoCodeId());
    }

    FiberQualityStandard standard =
        FiberQualityStandard.builder()
            .isoCode(isoCode)
            .standardName(request.getStandardName())
            .isDefault(makeDefault)
            .finenessMin(request.getFinenessMin())
            .finenessTarget(request.getFinenessTarget())
            .finenessMax(request.getFinenessMax())
            .lengthMmMin(request.getLengthMmMin())
            .lengthMmTarget(request.getLengthMmTarget())
            .lengthMmMax(request.getLengthMmMax())
            .strengthCndTexMin(request.getStrengthCndTexMin())
            .strengthCndTexTarget(request.getStrengthCndTexTarget())
            .strengthCndTexMax(request.getStrengthCndTexMax())
            .elongationPctMin(request.getElongationPctMin())
            .elongationPctTarget(request.getElongationPctTarget())
            .elongationPctMax(request.getElongationPctMax())
            .moisturePctMin(request.getMoisturePctMin())
            .moisturePctTarget(request.getMoisturePctTarget())
            .moisturePctMax(request.getMoisturePctMax())
            .trashContentPctMin(request.getTrashContentPctMin())
            .trashContentPctTarget(request.getTrashContentPctTarget())
            .trashContentPctMax(request.getTrashContentPctMax())
            .build();

    FiberQualityStandard saved = standardRepository.save(standard);
    log.info(
        "Created fiber quality standard: id={}, isoCodeId={}, name={}, default={}",
        saved.getId(),
        request.getIsoCodeId(),
        saved.getStandardName(),
        saved.getIsDefault());

    return FiberQualityStandardDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<FiberQualityStandardDto> getByIsoCodeId(UUID isoCodeId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting standards for ISO code: tenantId={}, isoCodeId={}", tenantId, isoCodeId);

    return standardRepository
        .findByTenantIdAndIsoCode_IdAndIsActiveTrue(tenantId, isoCodeId)
        .stream()
        .map(FiberQualityStandardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<FiberQualityStandardDto> getDefaultStandard(UUID isoCodeId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return standardRepository
        .findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(tenantId, isoCodeId)
        .map(FiberQualityStandardDto::from);
  }

  @Transactional(readOnly = true)
  public Optional<FiberQualityStandardDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return standardRepository.findByTenantIdAndId(tenantId, id).map(FiberQualityStandardDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberQualityStandardDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return standardRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberQualityStandardDto::from)
        .toList();
  }

  /** Returns all profiles for the tenant, grouped by iso_code_id. */
  @Transactional(readOnly = true)
  public List<FiberQualityStandardGroupDto> getAllGroupedByIsoCode() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<FiberQualityStandard> all = standardRepository.findByTenantIdAndIsActiveTrue(tenantId);

    Map<UUID, List<FiberQualityStandardDto>> byIsoCode =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    s -> s.getIsoCode().getId(),
                    Collectors.mapping(FiberQualityStandardDto::from, Collectors.toList())));

    return byIsoCode.entrySet().stream()
        .map(
            e ->
                FiberQualityStandardGroupDto.builder()
                    .isoCodeId(e.getKey())
                    .profiles(e.getValue())
                    .build())
        .toList();
  }

  @Transactional
  public FiberQualityStandardDto update(
      UUID standardId, UpdateFiberQualityStandardRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    FiberQualityStandard standard =
        standardRepository
            .findByTenantIdAndId(tenantId, standardId)
            .orElseThrow(() -> new NotFoundException("Quality standard not found: " + standardId));

    FiberIsoCode isoCode =
        fiberIsoCodeRepository
            .findById(request.getIsoCodeId())
            .orElseThrow(
                () -> new NotFoundException("ISO code not found: " + request.getIsoCodeId()));

    // Unique check: (iso_code_id, standard_name) must be unique, excluding current record
    if (standardRepository.existsByTenantIdAndIsoCode_IdAndStandardNameAndIdNot(
        tenantId, request.getIsoCodeId(), request.getStandardName(), standardId)) {
      throw new FiberDomainException(
          String.format(
              "Standard '%s' already exists for this ISO code", request.getStandardName()));
    }

    validateToleranceRanges(request);

    boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
    if (makeDefault) {
      clearExistingDefault(tenantId, request.getIsoCodeId());
    }

    standard.setIsoCode(isoCode);
    standard.setStandardName(request.getStandardName());
    standard.setIsDefault(makeDefault);
    standard.setFinenessMin(request.getFinenessMin());
    standard.setFinenessTarget(request.getFinenessTarget());
    standard.setFinenessMax(request.getFinenessMax());
    standard.setLengthMmMin(request.getLengthMmMin());
    standard.setLengthMmTarget(request.getLengthMmTarget());
    standard.setLengthMmMax(request.getLengthMmMax());
    standard.setStrengthCndTexMin(request.getStrengthCndTexMin());
    standard.setStrengthCndTexTarget(request.getStrengthCndTexTarget());
    standard.setStrengthCndTexMax(request.getStrengthCndTexMax());
    standard.setElongationPctMin(request.getElongationPctMin());
    standard.setElongationPctTarget(request.getElongationPctTarget());
    standard.setElongationPctMax(request.getElongationPctMax());
    standard.setMoisturePctMin(request.getMoisturePctMin());
    standard.setMoisturePctTarget(request.getMoisturePctTarget());
    standard.setMoisturePctMax(request.getMoisturePctMax());
    standard.setTrashContentPctMin(request.getTrashContentPctMin());
    standard.setTrashContentPctTarget(request.getTrashContentPctTarget());
    standard.setTrashContentPctMax(request.getTrashContentPctMax());

    FiberQualityStandard saved = standardRepository.save(standard);
    log.info(
        "Updated fiber quality standard: id={}, isoCodeId={}, name={}",
        saved.getId(),
        request.getIsoCodeId(),
        saved.getStandardName());

    return FiberQualityStandardDto.from(saved);
  }

  @Transactional
  public FiberQualityStandardDto setDefault(UUID standardId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    FiberQualityStandard standard =
        standardRepository
            .findByTenantIdAndId(tenantId, standardId)
            .orElseThrow(() -> new NotFoundException("Quality standard not found: " + standardId));

    clearExistingDefault(tenantId, standard.getIsoCode().getId());

    standard.setIsDefault(true);
    FiberQualityStandard saved = standardRepository.save(standard);
    log.info(
        "Set default quality standard: id={}, isoCodeId={}",
        saved.getId(),
        saved.getIsoCode().getId());

    return FiberQualityStandardDto.from(saved);
  }

  /**
   * Soft-deletes a quality standard. Returns a warning message if the deleted profile was the
   * default for its ISO code.
   */
  @Transactional
  public String delete(UUID standardId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    FiberQualityStandard standard =
        standardRepository
            .findByTenantIdAndId(tenantId, standardId)
            .orElseThrow(() -> new NotFoundException("Quality standard not found: " + standardId));

    boolean wasDefault = Boolean.TRUE.equals(standard.getIsDefault());

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

  private void clearExistingDefault(UUID tenantId, UUID isoCodeId) {
    standardRepository
        .findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(tenantId, isoCodeId)
        .ifPresent(
            existing -> {
              existing.setIsDefault(false);
              standardRepository.save(existing);
              log.debug("Cleared previous default standard: id={}", existing.getId());
            });
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
