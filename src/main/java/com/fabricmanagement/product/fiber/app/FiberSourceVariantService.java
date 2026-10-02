package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates tenant-private pure source variants of shared ISO codes (FIBER-CATALOG-1).
 *
 * <p>The variant references the shared ISO and category rows; nothing is copied and the canonical
 * undeclared fibre is never mutated. The tenant+ISO+source uniqueness index is the race backstop.
 * Callers bind both the Java tenant context and the database session to {@code tenantId} first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberSourceVariantService {

  private final FiberRepository fiberRepository;
  private final ProductRepository productRepository;

  /** The shared ISO/category pair a variant must reference, checked against the owner. */
  public record SharedPureReference(FiberIsoCode isoCode, FiberCategory category) {
    public SharedPureReference {
      if (isoCode == null || category == null) {
        throw new IllegalArgumentException("Shared ISO code and category are required");
      }
      if (!FiberCatalog.isOwner(isoCode.getTenantId())
          || !FiberCatalog.isOwner(category.getTenantId())) {
        throw new IllegalArgumentException("Variant references must belong to the catalogue");
      }
    }
  }

  @Transactional(readOnly = true)
  public Optional<Fiber> findActiveVariant(UUID tenantId, UUID isoCodeId, MaterialSource source) {
    return fiberRepository.findByTenantIdAndFiberIsoCode_IdAndMaterialSourceAndIsActiveTrue(
        tenantId, isoCodeId, source);
  }

  /**
   * Creates the private variant, or fails with {@code FIBER_REQUEST_VARIANT_EXISTS} (409) when the
   * tenant already has it; it never adopts or duplicates an existing row.
   */
  @Transactional
  public Fiber createPrivateVariant(
      UUID tenantId, SharedPureReference reference, String fiberName, MaterialSource source) {
    requireBoundTenant(tenantId);
    if (FiberCatalog.isOwner(tenantId)) {
      throw new FiberDomainException(
          "The shared catalogue does not own private source variants",
          "FIBER_CATALOG_OWNER_VARIANT_FORBIDDEN",
          400);
    }
    if (findActiveVariant(tenantId, reference.isoCode().getId(), source).isPresent()) {
      throw variantExists(reference.isoCode(), source);
    }
    Product product = productRepository.save(Product.create(ProductType.FIBER, "KG"));
    Fiber fiber =
        Fiber.createSourceVariant(
            product, reference.category(), reference.isoCode(), fiberName.trim(), source);
    try {
      Fiber saved = fiberRepository.saveAndFlush(fiber);
      log.info(
          "Private source variant created: tenant={}, iso={}, source={}, fiber={}",
          tenantId,
          reference.isoCode().getIsoCode(),
          source,
          saved.getId());
      return saved;
    } catch (DataIntegrityViolationException exception) {
      throw variantExists(reference.isoCode(), source);
    }
  }

  static FiberDomainException variantExists(FiberIsoCode isoCode, MaterialSource source) {
    return new FiberDomainException(
        "A fiber variant with this ISO code and material source already exists",
        "FIBER_REQUEST_VARIANT_EXISTS",
        409,
        new Object[] {isoCode.getIsoCode(), source});
  }

  private static void requireBoundTenant(UUID tenantId) {
    if (!tenantId.equals(TenantContext.getCurrentTenantIdOrNull())) {
      throw new IllegalStateException(
          "Private variants are created only inside the owning tenant's context");
    }
  }
}
