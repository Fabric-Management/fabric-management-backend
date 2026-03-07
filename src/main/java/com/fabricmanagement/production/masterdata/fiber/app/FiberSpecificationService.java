package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberSpecification;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberSpecificationRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberSpecificationDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberSpecificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiberSpecificationService {

  private final FiberSpecificationRepository specRepository;
  private final FiberRepository fiberRepository;

  @Transactional
  public FiberSpecificationDto create(CreateFiberSpecificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating fiber spec: tenantId={}, fiberId={}, specName={}",
        tenantId,
        request.getFiberId(),
        request.getSpecName());

    fiberRepository
        .findByTenantIdAndId(tenantId, request.getFiberId())
        .orElseThrow(() -> new NotFoundException("Fiber not found: " + request.getFiberId()));

    if (specRepository.existsByTenantIdAndFiberIdAndSpecName(
        tenantId, request.getFiberId(), request.getSpecName())) {
      throw new FiberDomainException(
          String.format("Specification '%s' already exists for this fiber", request.getSpecName()));
    }

    validateToleranceRanges(request);

    boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());

    if (makeDefault) {
      clearExistingDefault(tenantId, request.getFiberId());
    }

    FiberSpecification spec =
        FiberSpecification.builder()
            .fiberId(request.getFiberId())
            .specName(request.getSpecName())
            .isDefault(makeDefault)
            .testStandard(request.getTestStandard())
            .finenessMin(request.getFinenessMin())
            .finenessTarget(request.getFinenessTarget())
            .finenessMax(request.getFinenessMax())
            .lengthMin(request.getLengthMin())
            .lengthTarget(request.getLengthTarget())
            .lengthMax(request.getLengthMax())
            .strengthMin(request.getStrengthMin())
            .strengthTarget(request.getStrengthTarget())
            .strengthMax(request.getStrengthMax())
            .elongationMin(request.getElongationMin())
            .elongationTarget(request.getElongationTarget())
            .elongationMax(request.getElongationMax())
            .moistureMin(request.getMoistureMin())
            .moistureTarget(request.getMoistureTarget())
            .moistureMax(request.getMoistureMax())
            .trashContentMin(request.getTrashContentMin())
            .trashContentTarget(request.getTrashContentTarget())
            .trashContentMax(request.getTrashContentMax())
            .remarks(request.getRemarks())
            .build();

    FiberSpecification saved = specRepository.save(spec);
    log.info(
        "Created fiber spec: id={}, fiberId={}, name={}, default={}",
        saved.getId(),
        saved.getFiberId(),
        saved.getSpecName(),
        saved.getIsDefault());

    return FiberSpecificationDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<FiberSpecificationDto> getByFiberId(UUID fiberId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting specs for fiber: tenantId={}, fiberId={}", tenantId, fiberId);

    return specRepository.findByTenantIdAndFiberIdAndIsActiveTrue(tenantId, fiberId).stream()
        .map(FiberSpecificationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<FiberSpecificationDto> getDefaultSpec(UUID fiberId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return specRepository
        .findByTenantIdAndFiberIdAndIsDefaultTrueAndIsActiveTrue(tenantId, fiberId)
        .map(FiberSpecificationDto::from);
  }

  @Transactional(readOnly = true)
  public Optional<FiberSpecificationDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return specRepository.findByTenantIdAndId(tenantId, id).map(FiberSpecificationDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberSpecificationDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return specRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberSpecificationDto::from)
        .toList();
  }

  @Transactional
  public FiberSpecificationDto setDefault(UUID specId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    FiberSpecification spec =
        specRepository
            .findByTenantIdAndId(tenantId, specId)
            .orElseThrow(() -> new NotFoundException("Specification not found: " + specId));

    clearExistingDefault(tenantId, spec.getFiberId());

    spec.setIsDefault(true);
    FiberSpecification saved = specRepository.save(spec);
    log.info("Set default spec: id={}, fiberId={}", saved.getId(), saved.getFiberId());

    return FiberSpecificationDto.from(saved);
  }

  @Transactional
  public void delete(UUID specId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    FiberSpecification spec =
        specRepository
            .findByTenantIdAndId(tenantId, specId)
            .orElseThrow(() -> new NotFoundException("Specification not found: " + specId));

    spec.delete();
    specRepository.save(spec);
    log.info("Deleted fiber spec: id={}, name={}", spec.getId(), spec.getSpecName());
  }

  private void clearExistingDefault(UUID tenantId, UUID fiberId) {
    specRepository
        .findByTenantIdAndFiberIdAndIsDefaultTrueAndIsActiveTrue(tenantId, fiberId)
        .ifPresent(
            existing -> {
              existing.setIsDefault(false);
              specRepository.save(existing);
              log.debug("Cleared previous default spec: id={}", existing.getId());
            });
  }

  /**
   * Validates that min <= target <= max for each parameter where values are provided. DB
   * constraints also enforce this, but early validation gives better error messages.
   */
  private void validateToleranceRanges(CreateFiberSpecificationRequest req) {
    validateRange("Fineness", req.getFinenessMin(), req.getFinenessTarget(), req.getFinenessMax());
    validateRange("Length", req.getLengthMin(), req.getLengthTarget(), req.getLengthMax());
    validateRange("Strength", req.getStrengthMin(), req.getStrengthTarget(), req.getStrengthMax());
    validateRange(
        "Elongation", req.getElongationMin(), req.getElongationTarget(), req.getElongationMax());
    validateRange("Moisture", req.getMoistureMin(), req.getMoistureTarget(), req.getMoistureMax());
    validateRange(
        "Trash content",
        req.getTrashContentMin(),
        req.getTrashContentTarget(),
        req.getTrashContentMax());
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
