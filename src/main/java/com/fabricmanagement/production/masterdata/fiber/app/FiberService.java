package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.OptimisticLockConflictException;
import com.fabricmanagement.production.common.exception.ForbiddenOperationException;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import com.fabricmanagement.production.masterdata.fiber.domain.event.FiberCreatedEvent;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.production.masterdata.fiber.domain.exception.RecipeInUseException;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
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

/**
 * Fiber Service - Business logic for fiber management.
 *
 * <p>Implements FiberFacade for cross-module communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService implements FiberFacade {

  private final FiberRepository fiberRepository;
  private final ProductRepository productRepository;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final DomainEventPublisher eventPublisher;
  private final FiberValidationService validationService;
  private final BatchRepository batchRepository;

  /**
   * Create fiber (pure or blended).
   *
   * <p><b>Unified method:</b> Handles both pure and blended fibers.
   *
   * <ul>
   *   <li><b>Pure fiber:</b> composition is null or empty
   *   <li><b>Blended fiber:</b> composition contains base fiber IDs with percentages
   * </ul>
   *
   * @param request Unified fiber request (composition optional)
   * @return Created fiber
   */
  @Transactional
  public FiberDto createFiber(CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();
    log.info("Creating {} fiber: name={}", isBlended ? "blended" : "pure", request.getFiberName());

    UUID tenantId = TenantContext.requireTenantId();

    // PF4: Tenants cannot create pure fibers — only template/platform (FiberRequest approve flow)
    if (!isBlended && !TenantContext.TEMPLATE_TENANT_ID.equals(tenantId)) {
      throw new ForbiddenOperationException(
          "Pure fibers can only be created by the platform. Submit a fiber request instead.");
    }

    // Auto-create Product if productId is not provided (USER-FRIENDLY: Automation
    // reduces user
    // errors)
    Product product;
    if (request.getProductId() != null) {
      // Use existing Product
      product =
          productRepository
              .findByTenantIdAndId(tenantId, request.getProductId())
              .orElseThrow(
                  () ->
                      new FiberDomainException(
                          "Product not found or not accessible",
                          "FIBER_PRODUCT_NOT_FOUND",
                          404,
                          new Object[] {request.getProductId()}));
    } else {
      // Auto-create Product (USER-FRIENDLY: System handles Product creation
      // automatically)
      if (request.getUnit() == null || request.getUnit().isBlank()) {
        throw new FiberDomainException(
            "Unit is required when productId is not provided", "FIBER_UNIT_REQUIRED", 400);
      }

      log.info("Auto-creating Product: type=FIBER, unit={}", request.getUnit());
      product =
          Product.create(
              com.fabricmanagement.production.masterdata.product.domain.ProductType.FIBER,
              request.getUnit());
      product = productRepository.save(product);
      log.info("✅ Product auto-created: id={}, uid={}", product.getId(), product.getUid());
    }

    // Validate product type is FIBER
    if (product.getProductType()
        != com.fabricmanagement.production.masterdata.product.domain.ProductType.FIBER) {
      throw new FiberDomainException(
          "Product type must be FIBER",
          "FIBER_PRODUCT_TYPE_INVALID",
          400,
          new Object[] {product.getProductType()});
    }

    // Check if product already has a fiber detail
    UUID productIdToCheck = product.getId();
    if (fiberRepository.findByProductId(productIdToCheck).isPresent()) {
      throw new FiberDomainException(
          "Product already has fiber details", "FIBER_PRODUCT_ALREADY_USED", 409);
    }

    // Validate composition if blended
    if (isBlended) {
      validateBlendedFiber(request);
    }

    // Resolve category and ISO code (backend derives for blends)
    FiberCategory category = resolveFiberCategory(request);
    FiberIsoCode isoCode = resolveFiberIsoCode(request);

    // Generate suggested name for blended fiber if not provided
    String fiberName = request.getFiberName();
    if (isBlended && (fiberName == null || fiberName.isBlank())) {
      Map<UUID, String> baseFiberNames = new HashMap<>();
      for (UUID baseFiberId : request.getComposition().keySet()) {
        Fiber baseFiber =
            fiberRepository
                .findById(baseFiberId)
                .orElseThrow(
                    () ->
                        new FiberDomainException(
                            "Base fiber not found",
                            "FIBER_BASE_NOT_FOUND",
                            404,
                            new Object[] {baseFiberId}));
        baseFiberNames.put(baseFiberId, baseFiber.getFiberName());
      }
      fiberName = generateFiberName(request.getComposition(), baseFiberNames);
      log.info("Generated fiber name: {}", fiberName);
    }

    // Create fiber (pure or blended)
    Fiber fiber;
    if (isBlended) {
      fiber =
          Fiber.createBlendedFiber(product, category, isoCode, fiberName, request.getComposition());
    } else {
      fiber = Fiber.createPureFiber(product, category, isoCode, fiberName);
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
      throw new FiberDomainException(
          "Fiber with identical composition exists", "FIBER_DUPLICATE_COMPOSITION", 409);
    }

    // Validate circular references
    validationService.validateNoCircularReferences(composition);

    // Validate tenant consistency
    UUID currentTenantId = TenantContext.requireTenantId();
    validationService.validateTenantConsistency(composition, currentTenantId);
  }

  /** Resolve FiberCategory: for blends use MIXED_BLEND; for pure fibers use request value. */
  private FiberCategory resolveFiberCategory(CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();

    if (isBlended) {
      return fiberCategoryRepository
          .findByCategoryCode("MIXED_BLEND")
          .orElseGet(
              () ->
                  fiberCategoryRepository.findByIsActiveTrue().stream()
                      .filter(
                          c ->
                              c.getCategoryCode() != null
                                  && (c.getCategoryCode().contains("MIXED")
                                      || c.getCategoryCode().contains("BLEND")))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              new FiberDomainException(
                                  "No MIXED_BLEND category found",
                                  "FIBER_CATEGORY_MIXED_BLEND_MISSING",
                                  500)));
    }

    if (request.getFiberCategoryId() == null) {
      throw new FiberDomainException(
          "Fiber category ID is required for pure fibers", "FIBER_CATEGORY_REQUIRED", 400);
    }
    return fiberCategoryRepository
        .findById(request.getFiberCategoryId())
        .orElseThrow(
            () ->
                new FiberDomainException(
                    "Fiber category not found",
                    "FIBER_CATEGORY_NOT_FOUND",
                    404,
                    new Object[] {request.getFiberCategoryId()}));
  }

  /**
   * Resolve FiberIsoCode: for blends use primary (highest-percentage) base fiber's ISO; for pure
   * fibers use request value.
   */
  private FiberIsoCode resolveFiberIsoCode(CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();

    if (isBlended) {
      Map<UUID, BigDecimal> composition = request.getComposition();
      // Order by percentage desc, then by ISO code asc (tie-breaker for equal %)
      List<Fiber> baseFibers = fiberRepository.findAllById(composition.keySet());
      if (baseFibers.isEmpty()) {
        throw new FiberDomainException(
            "Blend composition is empty", "FIBER_COMPOSITION_EMPTY", 400);
      }
      Map<UUID, String> fiberIdToIso =
          baseFibers.stream()
              .collect(
                  Collectors.toMap(
                      Fiber::getId,
                      f ->
                          f.getFiberIsoCode() != null && f.getFiberIsoCode().getIsoCode() != null
                              ? f.getFiberIsoCode().getIsoCode()
                              : ""));

      UUID primaryFiberId =
          composition.entrySet().stream()
              .sorted(
                  Map.Entry.<UUID, BigDecimal>comparingByValue()
                      .reversed()
                      .thenComparing(e -> fiberIdToIso.getOrDefault(e.getKey(), "")))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElseThrow(
                  () ->
                      new FiberDomainException(
                          "Blend composition is empty", "FIBER_COMPOSITION_EMPTY", 400));

      Fiber primaryFiber =
          fiberRepository
              .findById(primaryFiberId)
              .orElseThrow(
                  () ->
                      new FiberDomainException(
                          "Base fiber not found",
                          "FIBER_BASE_NOT_FOUND",
                          404,
                          new Object[] {primaryFiberId}));

      if (primaryFiber.getFiberIsoCodeId() == null) {
        throw new FiberDomainException(
            "Primary base fiber has no ISO code",
            "FIBER_BASE_ISO_MISSING",
            400,
            new Object[] {primaryFiberId});
      }

      return fiberIsoCodeRepository
          .findById(primaryFiber.getFiberIsoCodeId())
          .orElseThrow(
              () ->
                  new FiberDomainException(
                      "Fiber ISO code not found",
                      "FIBER_ISO_NOT_FOUND",
                      404,
                      new Object[] {primaryFiber.getFiberIsoCodeId()}));
    }

    if (request.getFiberIsoCodeId() == null) {
      throw new FiberDomainException("Fiber ISO code required", "FIBER_ISO_REQUIRED", 400);
    }
    return fiberIsoCodeRepository
        .findById(request.getFiberIsoCodeId())
        .orElseThrow(
            () ->
                new FiberDomainException(
                    "Fiber ISO code not found",
                    "FIBER_ISO_NOT_FOUND",
                    404,
                    new Object[] {request.getFiberIsoCodeId()}));
  }

  @Transactional(readOnly = true)
  public Optional<FiberDto> getById(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Getting fiber: tenantId={}, id={}", tenantId, id);

    return fiberRepository.findByTenantIdInAndId(tenantScope(tenantId), id).map(FiberDto::from);
  }

  @Transactional(readOnly = true)
  public Optional<FiberDto> getByProductId(UUID productId) {
    log.debug("Getting fiber by productId: productId={}", productId);

    return fiberRepository.findByProductId(productId).map(FiberDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberDto> getAll() {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Getting all fibers: tenantId={}", tenantId);

    return fiberRepository
        .findByTenantIdInAndIsActiveTrueOrderByFiberName(tenantScope(tenantId))
        .stream()
        .map(FiberDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberDto> searchByName(String fiberName) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Searching fibers by name: tenantId={}, name={}", tenantId, fiberName);

    return fiberRepository
        .findByTenantIdInAndIsActiveTrueAndFiberNameContainingIgnoreCaseOrderByFiberName(
            tenantScope(tenantId), fiberName)
        .stream()
        .map(FiberDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findByNameContaining(String query) {
    return searchByName(query);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberCategoryDto> listActiveCategories() {
    return fiberCategoryRepository.findByIsActiveTrue().stream()
        .map(FiberCategoryDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findByProductIds(java.util.Collection<java.util.UUID> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return java.util.List.of();
    }

    // Each tenant has its own fiber data (cloned from template during onboarding)
    return fiberRepository.findByProductIdIn(new java.util.ArrayList<>(productIds)).stream()
        .map(FiberDto::from)
        .toList();
  }

  @Transactional
  public FiberDto updateFiber(UUID id, UpdateFiberRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    Fiber fiber =
        fiberRepository
            .findByTenantIdInAndId(tenantScope(tenantId), id)
            .orElseThrow(() -> new FiberDomainException("Fiber not found", "FIBER_NOT_FOUND", 404));

    rejectIfTemplateFiber(fiber);

    if (fiber.getStatus() == FiberStatus.OBSOLETE) {
      throw new FiberDomainException(
          "Fiber '"
              + fiber.getFiberName()
              + "' is OBSOLETE and cannot be updated. "
              + "Create a new fiber version instead.");
    }

    if (request.getVersion() != null && !request.getVersion().equals(fiber.getVersion())) {
      throw new OptimisticLockConflictException(
          "Fiber", id, request.getVersion(), fiber.getVersion());
    }

    fiber.update(request.getFiberName(), request.getRemarks());

    // Composition (recipe) change requires extra guards:
    // 1) Block if batches are RESERVED or IN_PROGRESS on the production floor
    // (immutability rule)
    // 2) Validate the new composition (percentages, circular refs, active base
    // fibers, etc.)
    if (request.getComposition() != null && !request.getComposition().isEmpty()) {
      if (batchRepository.existsByTenantIdAndProductIdAndStatusIn(
          tenantId, fiber.getProduct().getId(), BatchStatus.PRODUCTION_ACTIVE)) {
        throw new RecipeInUseException(id, fiber.getFiberName());
      }
      validateCompositionUpdate(request.getComposition());
      fiber.setComposition(request.getComposition());
    }

    Fiber saved = fiberRepository.save(fiber);

    log.info("Fiber updated: id={}", saved.getId());

    return FiberDto.from(saved);
  }

  /**
   * Validate composition on update — same business rules as creation, excluding duplicate check
   * against self.
   */
  private void validateCompositionUpdate(Map<UUID, BigDecimal> composition) {
    validationService.validateCompositionPercentages(composition);
    validationService.validateMinimumRatio(composition, FiberConstants.MIN_COMPONENT_PERCENTAGE);
    validationService.validateMaxComponents(composition, FiberConstants.MAX_BLEND_COMPONENTS);
    validationService.validateBaseFibersActive(composition);
    validationService.validateNoCircularReferences(composition);

    UUID tenantId = TenantContext.requireTenantId();
    validationService.validateTenantConsistency(composition, tenantId);
  }

  @Transactional
  public void deactivateFiber(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();

    Fiber fiber =
        fiberRepository
            .findByTenantIdInAndId(tenantScope(tenantId), id)
            .orElseThrow(() -> new FiberDomainException("Fiber not found", "FIBER_NOT_FOUND", 404));

    rejectIfTemplateFiber(fiber);

    if (batchRepository.existsByTenantIdAndProductIdAndStatusIn(
        tenantId, fiber.getProduct().getId(), BatchStatus.PRODUCTION_ACTIVE)) {
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
    return getById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberDto> findByProductId(UUID productId) {
    log.debug("FiberFacade: Finding fiber by productId: productId={}", productId);
    return fiberRepository.findByProductId(productId).map(FiberDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findAll() {
    return getAll();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    return fiberRepository.findByTenantIdInAndId(tenantScope(tenantId), id).isPresent();
  }

  /**
   * Check if a fiber with identical composition already exists.
   *
   * <p>Compares compositions by base fiber IDs and percentages.
   *
   * @param composition Map of baseFiberId → percentage
   * @return true if duplicate exists
   */
  private boolean isDuplicateComposition(Map<UUID, BigDecimal> composition) {
    UUID tenantId = TenantContext.requireTenantId();

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
      if (Math.abs(entry.getValue().subtract(percentage2).doubleValue())
          > FiberConstants.COMPOSITION_COMPARISON_TOLERANCE) {
        return false;
      }
    }

    return true;
  }

  /**
   * Generate a suggested name for a blended fiber based on composition.
   *
   * <p>Generates compact code format: COT60_LIN40_VIS20
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>60% Cotton + 40% Linen → "COT60_LIN40"
   *   <li>40% Cotton + 40% Linen + 20% Viscose → "COT40_LIN40_VIS20"
   * </ul>
   *
   * @param composition Map of baseFiberId → percentage
   * @param baseFiberNames Map of baseFiberId → fiberName
   * @return Suggested fiber name in compact code format
   */
  private String generateFiberName(
      Map<UUID, BigDecimal> composition, Map<UUID, String> baseFiberNames) {
    return composition.entrySet().stream()
        .sorted(
            (a, b) -> {
              int cmp = b.getValue().compareTo(a.getValue());
              if (cmp != 0) return cmp;
              String nameA = baseFiberNames.getOrDefault(a.getKey(), "");
              String nameB = baseFiberNames.getOrDefault(b.getKey(), "");
              return nameA.compareToIgnoreCase(nameB);
            })
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
   * <p>Examples:
   *
   * <ul>
   *   <li>"Cotton (100%)" → "COT"
   *   <li>"Linen (100%)" → "LIN"
   *   <li>"Viscose (100%)" → "VIS"
   *   <li>"Polyester (100%)" → "POL"
   *   <li>"Wool (100%)" → "WOL"
   *   <li>"Nylon (100%)" → "NYL"
   * </ul>
   *
   * <p>If fiber name doesn't match known patterns, uses first 3 uppercase letters.
   *
   * @param fiberName Full fiber name (e.g., "Cotton (100%)", "Recycled Cotton (100%)")
   * @return 3-letter uppercase code (e.g., "COT", "LIN", "VIS")
   */
  private String getFiberCode(String fiberName) {
    if (fiberName == null || fiberName.isBlank()) {
      return "XXX";
    }

    // Normalize: remove parentheses, percentages, and extra whitespace
    String normalized =
        fiberName
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

  // =====================================================
  // Template-tenant helpers
  // =====================================================

  /**
   * Returns the tenant scope for read queries: current tenant + template tenant.
   *
   * <p>This ensures tenant users see both their own fibers and the platform seed fibers from the
   * golden template tenant.
   */
  private List<UUID> tenantScope(UUID tenantId) {
    return List.of(tenantId, TenantContext.TEMPLATE_TENANT_ID);
  }

  /**
   * Guard: template-tenant fibers are read-only for non-template tenants.
   *
   * <p>Must be called in every mutating method (update, deactivate, status-change) after the fiber
   * lookup. When the read path is widened to include template fibers, the UI will know their IDs
   * and may send them to write endpoints — this guard prevents unauthorized mutations.
   *
   * @param fiber the fiber entity loaded from the database
   * @throws ForbiddenOperationException if a non-template tenant attempts to mutate a template
   *     fiber
   */
  private void rejectIfTemplateFiber(Fiber fiber) {
    UUID currentTenant = TenantContext.requireTenantId();
    if (TenantContext.TEMPLATE_TENANT_ID.equals(fiber.getTenantId())
        && !TenantContext.TEMPLATE_TENANT_ID.equals(currentTenant)) {
      throw new ForbiddenOperationException(
          "Template fibers are read-only and cannot be modified by tenants.");
    }
  }
}
