package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberCatalogScope;
import com.fabricmanagement.product.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps fibres to {@link FiberDto} with complete components, label and capabilities
 * (FIBER-CATALOG-1). Components of all blends in one call are resolved with one bulk query.
 */
@Component
@RequiredArgsConstructor
public class FiberDtoAssembler {

  private final FiberRepository fiberRepository;
  private final FiberCapabilityService capabilityService;

  public FiberDto toDto(Fiber fiber, UUID readingTenantId) {
    return toDtos(List.of(fiber), readingTenantId).getFirst();
  }

  public List<FiberDto> toDtos(List<Fiber> fibers, UUID readingTenantId) {
    if (fibers.isEmpty()) {
      return List.of();
    }
    Map<UUID, Fiber> known = new HashMap<>();
    fibers.forEach(fiber -> known.put(fiber.getId(), fiber));
    Set<UUID> missing = new HashSet<>();
    fibers.stream()
        .filter(Fiber::isBlended)
        .flatMap(fiber -> fiber.getComposition().keySet().stream())
        .filter(id -> !known.containsKey(id))
        .forEach(missing::add);
    if (!missing.isEmpty()) {
      fiberRepository
          .findScopedWithReferences(FiberCatalog.readScope(readingTenantId), missing)
          .forEach(component -> known.put(component.getId(), component));
    }
    boolean canWrite = capabilityService.currentCallerCanWrite();
    return fibers.stream().map(fiber -> map(fiber, known, readingTenantId, canWrite)).toList();
  }

  private FiberDto map(
      Fiber fiber, Map<UUID, Fiber> componentsById, UUID readingTenantId, boolean canWrite) {
    List<FiberCompositionComponentDto> components =
        FiberCompositionPresenter.components(fiber, componentsById);
    return FiberDto.builder()
        .id(fiber.getId())
        .tenantId(fiber.getTenantId())
        .uid(fiber.getUid())
        .productId(fiber.getProductId())
        .kind(fiber.getKind())
        .catalogScope(fiber.isShared() ? FiberCatalogScope.SHARED : FiberCatalogScope.TENANT)
        .fiberCategoryId(fiber.getFiberCategoryId())
        .fiberIsoCodeId(fiber.getFiberIsoCodeId())
        .category(
            fiber.getFiberCategory() != null
                ? FiberCategoryDto.from(fiber.getFiberCategory())
                : null)
        .isoCode(
            fiber.getFiberIsoCode() != null ? FiberIsoCodeDto.from(fiber.getFiberIsoCode()) : null)
        .fiberName(fiber.getFiberName())
        .materialSource(fiber.getMaterialSource())
        .status(fiber.getStatus())
        .remarks(fiber.getRemarks())
        .isActive(fiber.getIsActive())
        .version(fiber.getVersion())
        .createdAt(fiber.getCreatedAt())
        .updatedAt(fiber.getUpdatedAt())
        .composition(fiber.getComposition())
        .components(components)
        .compositionLabel(FiberCompositionPresenter.label(components))
        .capabilities(capabilityService.capabilities(fiber, readingTenantId, canWrite))
        .build();
  }
}
