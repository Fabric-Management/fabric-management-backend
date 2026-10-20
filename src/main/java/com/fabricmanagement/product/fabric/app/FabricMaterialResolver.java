package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.dto.FabricCompositionRequest;
import com.fabricmanagement.product.fabric.dto.FabricYarnRequest;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns the yarns and fibres a request names into article lines: each yarn must be one of the
 * tenant's live YARN products and keeps its designation as a snapshot; each fibre must be a pure
 * fibre with an ISO code, which it keeps with its name and material source (YARN-ARCHITECTURE Y5).
 */
@Component
@RequiredArgsConstructor
class FabricMaterialResolver {

  private final ProductRepository products;
  private final YarnFacade yarns;
  private final FiberFacade fibers;

  List<FabricArticleSpec.YarnLine> yarnLines(List<FabricYarnRequest> requested) {
    if (requested == null || requested.isEmpty()) return List.of();
    Set<UUID> ids =
        requested.stream().map(FabricYarnRequest::yarnProductId).collect(Collectors.toSet());
    Map<UUID, Product> found =
        products
            .findByTenantIdAndIdInAndProductType(
                TenantContext.requireTenantId(), ids, ProductType.YARN)
            .stream()
            .filter(product -> product.getDeletedAt() == null)
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    List<UUID> unknown = ids.stream().filter(id -> !found.containsKey(id)).sorted().toList();
    if (!unknown.isEmpty()) {
      throw new FabricDomainException("Not a yarn of this tenant: " + unknown);
    }
    Map<UUID, YarnArticleSummaryDto> summaries =
        yarns.findByProductIds(List.copyOf(ids)).stream()
            .collect(
                Collectors.toMap(
                    YarnArticleSummaryDto::productId, Function.identity(), (a, b) -> a));
    return requested.stream()
        .map(
            line ->
                new FabricArticleSpec.YarnLine(
                    line.role(),
                    line.yarnProductId(),
                    line.sharePercent(),
                    designation(
                        found.get(line.yarnProductId()), summaries.get(line.yarnProductId()))))
        .toList();
  }

  List<FabricArticleSpec.CompositionLine> compositionLines(
      List<FabricCompositionRequest> requested) {
    if (requested == null || requested.isEmpty()) return List.of();
    return requested.stream()
        .map(
            line -> {
              FiberDto fiber =
                  fibers
                      .findById(line.fiberId())
                      .orElseThrow(
                          () -> new FabricDomainException("Unknown fibre: " + line.fiberId()));
              if (fiber.getKind() != FiberKind.PURE) {
                throw new FabricDomainException(
                    "A fabric composition lists pure fibres; "
                        + fiber.getFiberName()
                        + " is a blend");
              }
              if (fiber.getIsoCode() == null || fiber.getIsoCode().getIsoCode() == null) {
                throw new FabricDomainException(
                    "Fibre " + fiber.getFiberName() + " has no ISO code to label it with");
              }
              return new FabricArticleSpec.CompositionLine(
                  fiber.getId(),
                  fiber.getIsoCode().getIsoCode(),
                  fiber.getFiberName(),
                  fiber.getMaterialSource(),
                  line.percentage());
            })
        .toList();
  }

  private static String designation(Product product, YarnArticleSummaryDto summary) {
    if (summary != null) {
      if (summary.canonicalDesignation() != null && !summary.canonicalDesignation().isBlank()) {
        return summary.canonicalDesignation();
      }
      if (summary.name() != null && !summary.name().isBlank()) {
        return summary.name();
      }
    }
    if (product.getName() != null && !product.getName().isBlank()) {
      return product.getName();
    }
    return product.getUid();
  }
}
