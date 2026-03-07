package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import com.fabricmanagement.production.execution.fiber.infra.repository.FiberBatchRepository;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import com.fabricmanagement.production.masterdata.fiber.domain.event.FiberCreatedEvent;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.RecipeInUseException;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fabricmanagement.production.common.exception.OptimisticLockConflictException;

/**
 * Fiber Service - Business logic for fiber management.
 *
 * <p>
 * Implements FiberFacade for cross-module communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService implements FiberFacade {

  private final FiberRepository fiberRepository;
  private final MaterialRepository materialRepository;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final DomainEventPublisher eventPublisher;
  private final FiberValidationService validationService;
  private final FiberBatchRepository fiberBatchRepository;

  /**
   * Create fiber (pure or blended).
   *
   * <p>
   * <b>Unified method:</b> Handles both pure and blended fibers.
   *
   * <ul>
   * <li><b>Pure fiber:</b> composition is null or empty
   * <li><b>Blended fiber:</b> composition contains base fiber IDs with
   * percentages
   * </ul>
   *
   * @param request Unified fiber request (composition optional)
   * @return Created fiber
   */
  @Transactional
  public FiberDto createFiber(CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();
    log.info("Creating {} fiber: name={}", isBlended ? "blended" : "pure", request.getFiberName());

    // Check if this is a pure 100% fiber being recreated
    if (!isBlended && request.getFiberIsoCodeId() != null) {
      throw new IllegalArgumentException(
          "Pure 100% fibers are pre-defined by the system. "
              + "You can only create blended fibers (combinations of existing fibers). "
              + "If you need a custom fiber, please use the default fiber types available.");
    }

    UUID tenantId = TenantContext.getCurrentTenantId();

    // Auto-create Material if materialId is not provided (USER-FRIENDLY: Automation
    // reduces user
    // errors)
    Material material;
    if (request.getMaterialId() != null) {
      // Use existing Material
      material = materialRepository
          .findByTenantIdAndId(tenantId, request.getMaterialId())
          .orElseThrow(
              () -> new IllegalArgumentException(
                  String.format(
                      "Material not found: %s (or not accessible for current tenant)",
                      request.getMaterialId())));
    } else {
      // Auto-create Material (USER-FRIENDLY: System handles Material creation
      // automatically)
      if (request.getUnit() == null || request.getUnit().isBlank()) {
        throw new IllegalArgumentException(
            "Unit is required when materialId is not provided. Material will be auto-created with type=FIBER.");
      }

      log.info("Auto-creating Material: type=FIBER, unit={}", request.getUnit());
      material = Material.create(
          com.fabricmanagement.production.masterdata.material.domain.MaterialType.FIBER,
          request.getUnit());
      material = materialRepository.save(material);
      log.info("✅ Material auto-created: id={}, uid={}", material.getId(), material.getUid());
    }

    // Validate material type is FIBER
    if (material.getMaterialType() != com.fabricmanagement.production.masterdata.material.domain.MaterialType.FIBER) {
      throw new IllegalArgumentException(
          "Material type must be FIBER, got: " + material.getMaterialType());
    }

    // Check if material already has a fiber detail
    UUID materialIdToCheck = material.getId();
    if (fiberRepository.findByMaterialId(materialIdToCheck).isPresent()) {
      throw new IllegalArgumentException(
          "Material already has fiber details. Each material can only have one fiber.");
    }

    // Validate composition if blended
    if (isBlended) {
      validateBlendedFiber(request);
    }

    // Load reference entities
    FiberCategory category = request.getFiberCategoryId() != null
        ? fiberCategoryRepository
            .findById(request.getFiberCategoryId())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Fiber category not found: " + request.getFiberCategoryId()))
        : null;

    FiberIsoCode isoCode = request.getFiberIsoCodeId() != null
        ? fiberIsoCodeRepository
            .findById(request.getFiberIsoCodeId())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Fiber ISO code not found: " + request.getFiberIsoCodeId()))
        : null;

    // Generate suggested name for blended fiber if not provided
    String fiberName = request.getFiberName();
    if (isBlended && (fiberName == null || fiberName.isBlank())) {
      Map<UUID, String> baseFiberNames = new HashMap<>();
      for (UUID baseFiberId : request.getComposition().keySet()) {
        Fiber baseFiber = fiberRepository
            .findById(baseFiberId)
            .orElseThrow(
                () -> new IllegalArgumentException("Base fiber not found: " + baseFiberId));
        baseFiberNames.put(baseFiberId, baseFiber.getFiberName());
      }
      fiberName = generateFiberName(request.getComposition(), baseFiberNames);
      log.info("Generated fiber name: {}", fiberName);
    }

    // Create fiber (pure or blended)
    Fiber fiber;
    if (isBlended) {
      fiber = Fiber.createBlendedFiber(
          material,
          category,
          isoCode,
          fiberName,
          request.getFiberGrade(),
          request.getComposition());
    } else {
      fiber = Fiber.createPureFiber(material, category, isoCode, fiberName, request.getFiberGrade());
    }

    fiber.setRemarks(request.getRemarks());
    Fiber saved = fiberRepository.save(fiber);

    // Publish domain event
    eventPublisher.publish(
        new FiberCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getFiberName(),
            saved.getFiberCategoryId(),
            saved.getFiberIsoCodeId()));

    log.info(
        "✅ {} fiber created: id={}, uid={}",
        isBlended ? "Blended" : "Pure",
        saved.getId(),
        saved.getUid());

    return FiberDto.from(saved);
  }

  /** Validate blended fiber composition. */
  private void validateBlendedFiber(CreateFiberRequest request) {
    Map<UUID, BigDecimal> composition = request.getComposition();

    // Validate composition percentages
    validationService.validateCompositionPercentages(composition);

    // Validate minimum ratio (no fiber less than configured minimum)
    validationService.validateMinimumRatio(composition, FiberConstants.MIN_COMPONENT_PERCENTAGE);

    // Validate maximum components (max configured number of fibers in a blend)
    validationService.validateMaxComponents(composition, FiberConstants.MAX_BLEND_COMPONENTS);

    // Validate base fibers exist and are active
    validationService.validateBaseFibersActive(composition);

    // Check if a fiber with identical composition already exists
    if (isDuplicateComposition(composition)) {
      throw new IllegalArgumentException(
          "A fiber with this exact composition already exists. Cannot create duplicate blend.");
    }

    // Validate circular references
    validationService.validateNoCircularReferences(composition);

    // Validate tenant consistency
    UUID currentTenantId = TenantContext.getCurrentTenantId();
    validationService.validateTenantConsistency(composition, currentTenantId);
  }

  @Transactional(readOnly = true)
  public Optional<FiberDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting fiber: tenantId={}, id={}", tenantId, id);

    return fiberRepository.findByTenantIdAndId(tenantId, id).map(FiberDto::from);
  }

  @Transactional(readOnly = true)
  public Optional<FiberDto> getByMaterialId(UUID materialId) {
    log.debug("Getting fiber by materialId: materialId={}", materialId);

    return fiberRepository.findByMaterialId(materialId).map(FiberDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all fibers: tenantId={}", tenantId);

    return fiberRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberDto> searchByName(String fiberName) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Searching fibers by name: tenantId={}, name={}", tenantId, fiberName);

    return fiberRepository
        .findByTenantIdAndFiberNameContainingIgnoreCase(tenantId, fiberName)
        .stream()
        .map(FiberDto::from)
        .toList();
  }

  @Transactional
  public FiberDto updateFiber(UUID id, CreateFiberRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Fiber fiber = fiberRepository
        .findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("Fiber not found"));

    if (fiber.getStatus() == FiberStatus.OBSOLETE) {
      throw new FiberDomainException(
          "Fiber '"
              + fiber.getFiberName()
              + "' is OBSOLETE and cannot be updated. "
              + "Create a new fiber version instead.");
    }

    if (request.getVersion() != null && !request.getVersion().equals(fiber.getVersion())) {
      throw new OptimisticLockConflictException("Fiber", id, request.getVersion(), fiber.getVersion());
    }

    fiber.update(request.getFiberName(), request.getFiberGrade(), request.getRemarks());

    // Composition (recipe) change requires extra guards:
    // 1) Block if batches are RESERVED or IN_PROGRESS on the production floor
    // (immutability rule)
    // 2) Validate the new composition (percentages, circular refs, active base
    // fibers, etc.)
    if (request.getComposition() != null && !request.getComposition().isEmpty()) {
      if (fiberBatchRepository.existsByTenantIdAndFiberIdAndStatusIn(
          tenantId, id, FiberBatchStatus.PRODUCTION_ACTIVE)) {
        throw new RecipeInUseException(id, fiber.getFiberName());
      }
      validateCompositionUpdate(request.getComposition(), id);
      fiber.setComposition(request.getComposition());
    }

    Fiber saved = fiberRepository.save(fiber);
    log.info("Fiber updated: id={}", saved.getId());

    return FiberDto.from(saved);
  }

  /**
   * Validate composition on update — same business rules as creation, excluding
   * duplicate check
   * against self.
   */
  private void validateCompositionUpdate(Map<UUID, BigDecimal> composition, UUID currentFiberId) {
    validationService.validateCompositionPercentages(composition);
    validationService.validateMinimumRatio(composition, FiberConstants.MIN_COMPONENT_PERCENTAGE);
    validationService.validateMaxComponents(composition, FiberConstants.MAX_BLEND_COMPONENTS);
    validationService.validateBaseFibersActive(composition);
    validationService.validateNoCircularReferences(composition);

    UUID tenantId = TenantContext.getCurrentTenantId();
    validationService.validateTenantConsistency(composition, tenantId);
  }

  @Transactional
  public void deactivateFiber(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Fiber fiber = fiberRepository
        .findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new IllegalArgumentException("Fiber not found"));

    if (fiberBatchRepository.existsByTenantIdAndFiberIdAndStatusIn(
        tenantId, id, FiberBatchStatus.PRODUCTION_ACTIVE)) {
      throw new FiberDomainException(
          "Fiber '"
              + fiber.getFiberName()
              + "' cannot be deactivated: it has batches currently RESERVED or IN_PROGRESS on the"
              + " production floor. Complete or cancel those batches first.");
    }

    fiber.delete();
    fiberRepository.save(fiber);

    log.info("Fiber deactivated: id={}", id);
  }

  // =====================================================
  // FiberFacade Implementation
  // =====================================================

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberDto> findById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("FiberFacade: Finding fiber: tenantId={}, id={}", tenantId, id);
    return fiberRepository.findByTenantIdAndId(tenantId, id).map(FiberDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberDto> findByMaterialId(UUID materialId) {
    log.debug("FiberFacade: Finding fiber by materialId: materialId={}", materialId);
    return fiberRepository.findByMaterialId(materialId).map(FiberDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findAll() {
    return getAll();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return fiberRepository.findByTenantIdAndId(tenantId, id).isPresent();
  }

  /**
   * Check if a fiber with identical composition already exists.
   *
   * <p>
   * Compares compositions by base fiber IDs and percentages.
   *
   * @param composition Map of baseFiberId → percentage
   * @return true if duplicate exists
   */
  private boolean isDuplicateComposition(Map<UUID, BigDecimal> composition) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Get all blended fibers for this tenant
    List<Fiber> allFibers = fiberRepository.findByTenantIdAndIsActiveTrue(tenantId);

    for (Fiber fiber : allFibers) {
      Map<UUID, BigDecimal> existingComposition = fiber.getComposition();

      // Check if composition maps are identical
      if (compositionsMatch(composition, existingComposition)) {
        log.warn(
            "Duplicate composition found: fiber={}, composition={}",
            fiber.getFiberName(),
            existingComposition);
        return true;
      }
    }

    return false;
  }

  /**
   * Compare two compositions to check if they match exactly.
   *
   * @param composition1 First composition
   * @param composition2 Second composition
   * @return true if compositions are identical
   */
  private boolean compositionsMatch(
      Map<UUID, BigDecimal> composition1, Map<UUID, BigDecimal> composition2) {
    // Empty compositions don't match
    if (composition1.isEmpty() && composition2.isEmpty()) {
      return false;
    }

    // Different sizes can't match
    if (composition1.size() != composition2.size()) {
      return false;
    }

    // Compare each entry
    for (Map.Entry<UUID, BigDecimal> entry : composition1.entrySet()) {
      BigDecimal percentage2 = composition2.get(entry.getKey());

      if (percentage2 == null) {
        return false;
      }

      // Compare percentages with configured tolerance
      if (Math.abs(
          entry.getValue().subtract(percentage2).doubleValue()) > FiberConstants.COMPOSITION_COMPARISON_TOLERANCE) {
        return false;
      }
    }

    return true;
  }

  /**
   * Generate a suggested name for a blended fiber based on composition.
   *
   * <p>
   * Generates compact code format: COT60_LIN40_VIS20
   *
   * <p>
   * Examples:
   *
   * <ul>
   * <li>60% Cotton + 40% Linen → "COT60_LIN40"
   * <li>40% Cotton + 40% Linen + 20% Viscose → "COT40_LIN40_VIS20"
   * </ul>
   *
   * @param composition    Map of baseFiberId → percentage
   * @param baseFiberNames Map of baseFiberId → fiberName
   * @return Suggested fiber name in compact code format
   */
  private String generateFiberName(
      Map<UUID, BigDecimal> composition, Map<UUID, String> baseFiberNames) {
    return composition.entrySet().stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by percentage descending
        .map(
            entry -> {
              String fiberName = baseFiberNames.get(entry.getKey());
              String code = getFiberCode(fiberName);
              BigDecimal percentage = entry.getValue();
              return String.format("%s%.0f", code, percentage);
            })
        .collect(Collectors.joining("_"));
  }

  /**
   * Get fiber code abbreviation from fiber name.
   *
   * <p>
   * Examples:
   *
   * <ul>
   * <li>"Cotton (100%)" → "COT"
   * <li>"Linen (100%)" → "LIN"
   * <li>"Viscose (100%)" → "VIS"
   * <li>"Polyester (100%)" → "POL"
   * <li>"Wool (100%)" → "WOL"
   * <li>"Nylon (100%)" → "NYL"
   * </ul>
   *
   * <p>
   * If fiber name doesn't match known patterns, uses first 3 uppercase letters.
   *
   * @param fiberName Full fiber name (e.g., "Cotton (100%)", "Recycled Cotton
   *                  (100%)")
   * @return 3-letter uppercase code (e.g., "COT", "LIN", "VIS")
   */
  private String getFiberCode(String fiberName) {
    if (fiberName == null || fiberName.isBlank()) {
      return "XXX";
    }

    // Normalize: remove parentheses, percentages, and extra whitespace
    String normalized = fiberName
        .replaceAll("\\(.*?\\)", "") // Remove (100%) etc.
        .replaceAll("%", "") // Remove % signs
        .replaceAll("\\s+", " ") // Normalize whitespace
        .trim()
        .toLowerCase();

    // Known fiber type mappings (case-insensitive)
    if (normalized.contains("cotton")) {
      return "COT";
    } else if (normalized.contains("linen")) {
      return "LIN";
    } else if (normalized.contains("viscose")) {
      return "VIS";
    } else if (normalized.contains("polyester")) {
      return "POL";
    } else if (normalized.contains("wool")) {
      return "WOL";
    } else if (normalized.contains("nylon")) {
      return "NYL";
    } else if (normalized.contains("elastane") || normalized.contains("elastan")) {
      return "ELA";
    } else if (normalized.contains("polypropylene")) {
      return "PPE";
    } else if (normalized.contains("acrylic")) {
      return "ACR";
    } else if (normalized.contains("silk")) {
      return "SIL";
    } else if (normalized.contains("recycled") && normalized.contains("cotton")) {
      return "RCOT"; // Recycled Cotton
    }

    // Fallback: Extract first 3 uppercase letters from original name
    String upper = normalized.toUpperCase().replaceAll("[^A-Z]", "");
    if (upper.length() >= 3) {
      return upper.substring(0, 3);
    } else if (upper.length() > 0) {
      // Pad with X if less than 3 letters
      return String.format("%-3s", upper).replace(' ', 'X').substring(0, 3);
    }

    return "XXX"; // Unknown fiber type
  }
}
