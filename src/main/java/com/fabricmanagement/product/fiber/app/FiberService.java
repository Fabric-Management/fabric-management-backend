package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.OptimisticLockConflictException;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.app.port.FiberUsagePort;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.event.FiberCreatedEvent;
import com.fabricmanagement.product.fiber.domain.event.FiberMaterialSourceDeclaredEvent;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.exception.RecipeInUseException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberCatalogReferenceDto;
import com.fabricmanagement.product.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fibre catalogue commands and reads (FIBER-CATALOG-1).
 *
 * <p>Tenants read the shared catalogue plus their own rows. Shared canonical pure fibres (and the
 * products behind them) are read-only for tenants: update, deactivate and source declaration fail
 * with {@code FIBER_SHARED_READ_ONLY}; another tenant's private fibre is simply not found. Tenants
 * create blends from shared or own pure fibres; a blend carries no ISO code and no single source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService implements FiberFacade {

  private final FiberRepository fiberRepository;
  private final ProductRepository productRepository;
  private final FiberReferenceQueryService referenceQueryService;
  private final DomainEventPublisher eventPublisher;
  private final FiberValidationService validationService;
  private final FiberUsagePort fiberUsagePort;
  private final FiberDtoAssembler dtoAssembler;
  private final ObjectMapper objectMapper;

  // =====================================================
  // Commands
  // =====================================================

  /**
   * Creates a blend (tenant) or publishes a canonical pure fibre (catalogue owner only).
   *
   * <p>A non-empty composition means a blend: it must not carry an ISO code or a material source
   * (named 400s, never silently ignored); category is always the shared MIXED_BLEND. An empty or
   * absent composition means a canonical pure fibre, which only the catalogue owner may publish;
   * tenants obtain private pure variants through the reviewed request flow.
   */
  @Override
  @Transactional
  public FiberDto createFiber(CreateFiberRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    boolean blend = request.getComposition() != null && !request.getComposition().isEmpty();
    Fiber saved = blend ? createBlend(request, tenantId) : publishCanonicalPure(request, tenantId);

    eventPublisher.publish(
        new FiberCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getFiberName(),
            saved.getFiberCategoryId(),
            saved.getFiberIsoCodeId()));
    log.info(
        "Fiber created: id={}, kind={}, tenantId={}", saved.getId(), saved.getKind(), tenantId);
    return dtoAssembler.toDto(saved, tenantId);
  }

  private Fiber createBlend(CreateFiberRequest request, UUID tenantId) {
    if (FiberCatalog.isOwner(tenantId)) {
      throw new FiberDomainException(
          "The shared catalogue publishes pure fibres only; blends belong to tenants",
          "FIBER_CATALOG_OWNER_BLEND_FORBIDDEN",
          400);
    }
    if (request.getFiberIsoCodeId() != null) {
      throw new FiberDomainException(
          "A blend has no ISO code of its own; its components carry the shared ISO codes",
          "FIBER_BLEND_ISO_FORBIDDEN",
          400);
    }
    if (request.getMaterialSource() != null) {
      throw new FiberDomainException(
          "A blended fiber cannot carry one material source",
          "FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN",
          400);
    }
    FiberCategory mixedBlend = referenceQueryService.requireMixedBlendCategory();
    if (request.getFiberCategoryId() != null
        && !request.getFiberCategoryId().equals(mixedBlend.getId())) {
      throw new FiberDomainException(
          "A blend always belongs to the shared MIXED_BLEND category",
          "FIBER_BLEND_CATEGORY_INVALID",
          400,
          new Object[] {request.getFiberCategoryId()});
    }

    FiberValidationService.ResolvedComposition resolved =
        validationService.validateBlendDefinition(request.getComposition(), tenantId);
    rejectDuplicateComposition(tenantId, resolved.composition(), FiberRepository.NO_FIBER);

    String fiberName = trimToNull(request.getFiberName());
    if (fiberName == null) {
      List<FiberCompositionComponentDto> components =
          FiberCompositionPresenter.ordered(
              resolved.composition().entrySet().stream()
                  .map(
                      entry -> {
                        Fiber component = resolved.fibers().get(entry.getKey());
                        return new FiberCompositionComponentDto(
                            component.getId(),
                            component.getFiberIsoCodeId(),
                            component.getFiberIsoCode().getIsoCode(),
                            component.getFiberName(),
                            entry.getValue(),
                            component.getMaterialSource());
                      })
                  .toList());
      fiberName = FiberCompositionPresenter.label(components);
    }

    Fiber fiber =
        Fiber.createBlend(
            ownFiberProduct(request, tenantId), mixedBlend, fiberName, resolved.composition());
    fiber.setRemarks(request.getRemarks());
    try {
      return fiberRepository.saveAndFlush(fiber);
    } catch (DataIntegrityViolationException exception) {
      throw duplicateComposition();
    }
  }

  private Fiber publishCanonicalPure(CreateFiberRequest request, UUID tenantId) {
    if (!FiberCatalog.isOwner(tenantId)) {
      throw new FiberDomainException(
          "Pure fibres are published by the platform catalogue. Submit a fiber request instead.",
          "FIBER_PURE_CREATION_PLATFORM_ONLY",
          403);
    }
    if (request.getMaterialSource() != null) {
      throw new FiberDomainException(
          "A canonical shared fibre never declares a material source",
          "FIBER_CANONICAL_SOURCE_FORBIDDEN",
          400);
    }
    String fiberName = trimToNull(request.getFiberName());
    if (fiberName == null) {
      throw new FiberDomainException("Fiber name is required", "FIBER_NAME_REQUIRED", 400);
    }
    FiberIsoCode isoCode =
        referenceQueryService
            .findIsoCodeById(request.getFiberIsoCodeId())
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "Shared ISO code not found",
                        "FIBER_ISO_NOT_FOUND",
                        404,
                        new Object[] {request.getFiberIsoCodeId()}));
    FiberCategory category =
        referenceQueryService
            .findCategoryById(request.getFiberCategoryId())
            .orElseThrow(
                () ->
                    new FiberDomainException(
                        "Shared fiber category not found",
                        "FIBER_CATEGORY_NOT_FOUND",
                        404,
                        new Object[] {request.getFiberCategoryId()}));
    if (isoCode.getFiberType() == null
        || !isoCode.getFiberType().equals(category.getCategoryCode())) {
      throw new FiberDomainException(
          "Category does not match the shared ISO code's fibre type",
          "FIBER_CATEGORY_MISMATCH",
          400,
          new Object[] {category.getCategoryCode(), isoCode.getFiberType()});
    }
    Fiber fiber =
        Fiber.createCanonicalPure(ownFiberProduct(request, tenantId), category, isoCode, fiberName);
    fiber.setRemarks(request.getRemarks());
    try {
      return fiberRepository.saveAndFlush(fiber);
    } catch (DataIntegrityViolationException exception) {
      throw new FiberDomainException(
          "A canonical fibre is already published for this ISO code",
          "FIBER_CATALOG_CODE_ALREADY_PUBLISHED",
          409,
          new Object[] {isoCode.getIsoCode()});
    }
  }

  /** An explicitly referenced product must be the caller's own unused FIBER product. */
  private Product ownFiberProduct(CreateFiberRequest request, UUID tenantId) {
    Product product;
    if (request.getProductId() != null) {
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
      if (product.getProductType() != ProductType.FIBER) {
        throw new FiberDomainException(
            "Product type must be FIBER",
            "FIBER_PRODUCT_TYPE_INVALID",
            400,
            new Object[] {product.getProductType()});
      }
      if (fiberRepository.findInScopeByProductId(List.of(tenantId), product.getId()).isPresent()) {
        throw new FiberDomainException(
            "Product already has fiber details", "FIBER_PRODUCT_ALREADY_USED", 409);
      }
      return product;
    }
    if (request.getUnit() == null || request.getUnit().isBlank()) {
      throw new FiberDomainException(
          "Unit is required when productId is not provided", "FIBER_UNIT_REQUIRED", 400);
    }
    return productRepository.save(Product.create(ProductType.FIBER, request.getUnit()));
  }

  @Transactional
  public FiberDto updateFiber(UUID id, UpdateFiberRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Fiber fiber = loadForMutation(id, tenantId);

    if (fiber.getStatus() == FiberStatus.OBSOLETE) {
      throw new FiberDomainException(
          "Fiber '" + fiber.getFiberName() + "' is OBSOLETE and cannot be updated.",
          "FIBER_OBSOLETE",
          409);
    }
    if (request.getVersion() != null && !request.getVersion().equals(fiber.getVersion())) {
      throw new OptimisticLockConflictException(
          "Fiber", id, request.getVersion(), fiber.getVersion());
    }

    Map<UUID, BigDecimal> requested = request.getComposition();
    MaterialSource declaredSource = request.getMaterialSource();
    if (requested != null) {
      applyCompositionChange(fiber, requested, tenantId);
    }
    if (declaredSource != null) {
      if (fiber.isShared()) {
        throw new FiberDomainException(
            "A canonical shared fibre never declares a material source",
            "FIBER_CANONICAL_SOURCE_FORBIDDEN",
            409);
      }
      fiber.declareMaterialSource(declaredSource);
    }
    fiber.update(request.getFiberName().trim(), request.getRemarks());

    Fiber saved;
    try {
      saved = fiberRepository.saveAndFlush(fiber);
    } catch (DataIntegrityViolationException exception) {
      throw duplicateComposition();
    }

    if (declaredSource != null) {
      UUID actorId =
          TenantContext.getCurrentUserId() != null
              ? TenantContext.getCurrentUserId()
              : TenantContext.SYSTEM_ACTOR_ID;
      eventPublisher.publish(
          new FiberMaterialSourceDeclaredEvent(
              saved.getTenantId(), saved.getId(), null, declaredSource, actorId));
    }
    log.info("Fiber updated: id={}", saved.getId());
    return dtoAssembler.toDto(saved, tenantId);
  }

  /**
   * {@code null} leaves the composition unchanged; an empty map or a pure/blend kind change is
   * rejected. A blend change obeys the same rules as creation and is refused while batches are
   * reserved or in progress. Existing batches keep their own composition snapshot.
   */
  private void applyCompositionChange(Fiber fiber, Map<UUID, BigDecimal> requested, UUID tenantId) {
    if (requested.isEmpty()) {
      throw new FiberDomainException(
          "Composition cannot be cleared; omit it to keep the current composition",
          "FIBER_COMPOSITION_EMPTY",
          400);
    }
    if (fiber.isPure()) {
      throw new FiberDomainException(
          "A pure fibre cannot become a blend", "FIBER_KIND_CHANGE_FORBIDDEN", 400);
    }
    FiberValidationService.ResolvedComposition resolved =
        validationService.validateBlendDefinition(requested, tenantId);
    if (FiberComposition.sameComposition(resolved.composition(), fiber.getComposition())) {
      return;
    }
    if (fiberUsagePort.isFiberInActiveProduction(tenantId, fiber.getProductId())) {
      throw new RecipeInUseException(fiber.getId(), fiber.getFiberName());
    }
    rejectDuplicateComposition(tenantId, resolved.composition(), fiber.getId());
    fiber.changeBlendComposition(resolved.composition());
  }

  @Transactional
  public void deactivateFiber(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    Fiber fiber = loadForMutation(id, tenantId);

    if (fiberUsagePort.isFiberInActiveProduction(tenantId, fiber.getProductId())) {
      throw new FiberDomainException(
          "Fiber '"
              + fiber.getFiberName()
              + "' cannot be deactivated: it has batches currently RESERVED or IN_PROGRESS on the"
              + " production floor. Complete or cancel those batches first.",
          "FIBER_IN_ACTIVE_PRODUCTION",
          409);
    }
    fiber.delete();
    fiberRepository.save(fiber);
    log.info("Fiber deactivated: id={}", id);
  }

  /**
   * Loads a fibre visible to the tenant for a mutation. Another tenant's private fibre is not found
   * (404, ownership not revealed); a shared catalogue fibre is visible but read-only (403).
   */
  private Fiber loadForMutation(UUID id, UUID tenantId) {
    Fiber fiber =
        fiberRepository
            .findByTenantIdInAndId(FiberCatalog.readScope(tenantId), id)
            .orElseThrow(() -> new FiberDomainException("Fiber not found", "FIBER_NOT_FOUND", 404));
    if (fiber.isShared() && !FiberCatalog.isOwner(tenantId)) {
      throw sharedReadOnly();
    }
    if (!Boolean.TRUE.equals(fiber.getIsActive())) {
      throw new FiberDomainException("Fiber is inactive", "FIBER_INACTIVE", 409);
    }
    return fiber;
  }

  static FiberDomainException sharedReadOnly() {
    return new FiberDomainException(
        "Shared catalogue fibres are read-only for tenants", "FIBER_SHARED_READ_ONLY", 403);
  }

  private void rejectDuplicateComposition(
      UUID tenantId, Map<UUID, BigDecimal> normalized, UUID excludeFiberId) {
    fiberRepository.acquireCompositionLock(FiberComposition.lockKey(tenantId, normalized));
    if (fiberRepository
        .findActiveBlendIdByComposition(tenantId, toJson(normalized), excludeFiberId)
        .isPresent()) {
      throw duplicateComposition();
    }
  }

  private static FiberDomainException duplicateComposition() {
    return new FiberDomainException(
        "Fiber with identical composition exists", "FIBER_DUPLICATE_COMPOSITION", 409);
  }

  private String toJson(Map<UUID, BigDecimal> composition) {
    try {
      return objectMapper.writeValueAsString(composition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Composition cannot be serialised", exception);
    }
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  // =====================================================
  // Reads (current tenant + shared catalogue)
  // =====================================================

  @Transactional(readOnly = true)
  public Optional<FiberDto> getById(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    return fiberRepository
        .findByTenantIdInAndId(FiberCatalog.readScope(tenantId), id)
        .map(fiber -> dtoAssembler.toDto(fiber, tenantId));
  }

  @Transactional(readOnly = true)
  public Optional<FiberDto> getByProductId(UUID productId) {
    UUID tenantId = TenantContext.requireTenantId();
    return fiberRepository
        .findInScopeByProductId(FiberCatalog.readScope(tenantId), productId)
        .map(fiber -> dtoAssembler.toDto(fiber, tenantId));
  }

  @Transactional(readOnly = true)
  public List<FiberDto> getAll() {
    UUID tenantId = TenantContext.requireTenantId();
    return dtoAssembler.toDtos(
        fiberRepository.findActiveInScope(FiberCatalog.readScope(tenantId)), tenantId);
  }

  @Transactional(readOnly = true)
  public List<FiberDto> searchByName(String fiberName) {
    UUID tenantId = TenantContext.requireTenantId();
    String query = fiberName == null ? "" : fiberName.trim();
    return dtoAssembler.toDtos(
        fiberRepository.searchActiveInScope(FiberCatalog.readScope(tenantId), query), tenantId);
  }

  // =====================================================
  // FiberFacade
  // =====================================================

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberDto> findById(UUID id) {
    return getById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberDto> findByProductId(UUID productId) {
    return getByProductId(productId);
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
    return fiberRepository.findByTenantIdInAndId(FiberCatalog.readScope(tenantId), id).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findByNameContaining(String query) {
    return searchByName(query);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberCategoryDto> listActiveCategories() {
    return referenceQueryService.listCategories();
  }

  @Override
  @Transactional(readOnly = true)
  public List<FiberDto> findByProductIds(Collection<UUID> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    return dtoAssembler.toDtos(
        fiberRepository.findInScopeByProductIds(FiberCatalog.readScope(tenantId), productIds),
        tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FiberCatalogReferenceDto> findCanonicalByIsoCode(String isoCode) {
    return fiberRepository
        .findCanonicalByIsoCode(
            FiberCatalog.OWNER_ID, FiberReferenceQueryService.normalizeIsoCode(isoCode))
        .map(
            fiber ->
                new FiberCatalogReferenceDto(
                    fiber.getId(),
                    fiber.getProductId(),
                    fiber.getFiberIsoCodeId(),
                    fiber.getFiberIsoCode().getIsoCode(),
                    fiber.getFiberName()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findOwnBlendProductId(Map<UUID, BigDecimal> composition) {
    UUID tenantId = TenantContext.requireTenantId();
    if (composition == null || composition.size() < 2) {
      return Optional.empty();
    }
    return fiberRepository
        .findActiveBlendIdByComposition(
            tenantId, toJson(FiberComposition.normalize(composition)), FiberRepository.NO_FIBER)
        .flatMap(id -> fiberRepository.findByTenantIdAndId(tenantId, id))
        .map(Fiber::getProductId);
  }
}
