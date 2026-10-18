package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.color.app.ColorService;
import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.app.ProductSalesDefinitionService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.dto.UpdateProductSalesDefinitionRequest;
import com.fabricmanagement.product.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.app.LotEvidenceService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.batch.dto.LotEvidenceDtos;
import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named, coloured fabric examples installed inside the trusted sales playground seed transaction.
 * Product width options and synthetic lot measurements are separate records; a name is never parsed
 * as measurement evidence. No startup, login, reset or ordinary tenant invokes this helper.
 */
@Component
@RequiredArgsConstructor
public class FabricStockDemoSeeder {

  record Fabric(String code, String name, String colour, int gsm, String price) {}

  static final List<Fabric> FABRICS =
      List.of(
          new Fabric("P1454", "PM-1454 Cotton Poplin 120gsm (demo)", "NAVY-01", 120, "8.60"),
          new Fabric("P1455", "PM-1455 Cotton Twill 220gsm (demo)", "CHAR-03", 220, "9.20"),
          new Fabric("P1456", "PM-1456 Cotton Satin 160gsm (demo)", "ECRU-02", 160, "11.40"),
          new Fabric("P1457", "PM-1457 Linen Blend 180gsm (demo)", "RUST-04", 180, "12.80"),
          new Fabric("P1458", "PM-1458 Single Jersey 180gsm (demo)", "IND-05", 180, "6.75"));
  static final List<Integer> WIDTHS_CM = List.of(150, 160);
  static final String MEASUREMENT_NOTE =
      "Playground example: synthetic finished-width measurement, not a real inspection.";

  private final ProductFacade products;
  private final SalesProductService catalogue;
  private final ProductSalesDefinitionService definitions;
  private final ColorService colours;
  private final QualityGradeService grades;
  private final BatchService batches;
  private final LotEvidenceService evidence;
  private final StockUnitService pieces;

  @Transactional(propagation = Propagation.MANDATORY)
  public void seedFor(UUID tenantId) {
    if (!Objects.equals(tenantId, TenantContext.requireTenantId())) {
      throw new IllegalArgumentException("Fabric fixtures require their own tenant context");
    }
    UUID grade = grades.findByCode(ProductType.FABRIC, "1ST").getId();
    for (Fabric fabric : FABRICS) {
      UUID colour =
          colours.list(true).stream()
              .filter(card -> fabric.colour().equals(card.getCode()))
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("Demo colour missing: " + fabric.colour()))
              .getId();
      ProductDto product = product(fabric);
      ensureWidths(product.getId(), WIDTHS_CM);
      for (int width : WIDTHS_CM) {
        BatchDto main =
            lot(
                fabric,
                product,
                colour,
                grade,
                width,
                "A",
                java.util.Collections.nCopies(12, "125"));
        BatchDto remnant =
            lot(fabric, product, colour, grade, width, "R", List.of("95", "95", "100"));
        List<UUID> ids = List.of(main.getId(), remnant.getId());
        if (evidence.compatibilityFor(main.getId()).stream()
            .noneMatch(
                confirmation ->
                    confirmation.customerId() == null
                        && confirmation.batchIds().containsAll(ids))) {
          evidence.confirmCompatibility(
              new LotEvidenceDtos.ConfirmCompatibilityRequest(
                  ids,
                  null,
                  "Synthetic playground example: the two lots at "
                      + width
                      + " cm may be combined."));
        }
      }
    }
    legacyGabardineWidth();
  }

  private ProductDto product(Fabric fabric) {
    return catalogue.getActiveCatalogForModule("FABRIC").stream()
        .filter(
            entry -> entry.getCustomerId() == null && fabric.name().equals(entry.getProductName()))
        .findFirst()
        .flatMap(entry -> products.findById(TenantContext.requireTenantId(), entry.getProductId()))
        .orElseGet(
            () -> {
              ProductDto created =
                  products.createProduct(
                      CreateProductRequest.builder()
                          .productType(ProductType.FABRIC)
                          .unit("M")
                          .build());
              catalogue.createEntry(
                  new CreateSalesProductRequest(
                      created.getId(),
                      fabric.name(),
                      "FABRIC",
                      new BigDecimal(fabric.price()),
                      "GBP",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null));
              return created;
            });
  }

  private void ensureWidths(UUID productId, List<Integer> required) {
    var current = definitions.get(productId);
    var widths =
        new ArrayList<>(
            current.finishedWidths().stream()
                .map(
                    width ->
                        new UpdateProductSalesDefinitionRequest.FinishedWidthInput(
                            width.value(), width.unit()))
                .toList());
    for (int width : required) {
      if (widths.stream()
          .noneMatch(
              option ->
                  "CM".equals(option.unit())
                      && option.value().compareTo(BigDecimal.valueOf(width)) == 0)) {
        widths.add(
            new UpdateProductSalesDefinitionRequest.FinishedWidthInput(
                BigDecimal.valueOf(width), "CM"));
      }
    }
    if (widths.size() != current.finishedWidths().size()) {
      definitions.replace(
          productId, new UpdateProductSalesDefinitionRequest(current.extraSalesUnits(), widths));
    }
  }

  private BatchDto lot(
      Fabric fabric,
      ProductDto product,
      UUID colour,
      UUID grade,
      int width,
      String suffix,
      List<String> lengths) {
    String code = "PG-FAB-" + fabric.code() + "-" + width + "-" + suffix;
    var existing = batches.findByBatchCode(code);
    if (existing.isPresent()) return existing.get();
    BigDecimal total =
        lengths.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    BatchDto batch =
        batches.create(
            CreateBatchRequest.builder()
                .productId(product.getId())
                .productType(ProductType.FABRIC)
                .colorId(colour)
                .batchCode(code)
                .quantity(total)
                .unit("M")
                .sourceType(BatchSourceType.INITIAL_STOCK)
                .remarks("Illustrative playground stock; " + width + " cm finished width.")
                .build());
    evidence.recordFinishedWidth(
        batch.getId(),
        new LotEvidenceDtos.RecordFinishedWidthRequest(
            BigDecimal.valueOf(width), "CM", MEASUREMENT_NOTE));
    for (int index = 0; index < lengths.size(); index++) {
      BigDecimal length = new BigDecimal(lengths.get(index));
      BigDecimal weight =
          length
              .multiply(BigDecimal.valueOf(width))
              .multiply(BigDecimal.valueOf(fabric.gsm()))
              .divide(new BigDecimal("100000"), 3, RoundingMode.HALF_UP);
      var piece =
          pieces.create(
              batch.getId(),
              ProductType.FABRIC,
              code + "-" + String.format("%02d", index + 1),
              null,
              PackageType.ROLL,
              weight,
              null,
              "KG",
              length,
              "M",
              null,
              StockUnitSourceType.PRODUCTION,
              batch.getId());
      pieces.changeGrade(piece.getId(), grade, "Illustrative playground first-quality roll", null);
    }
    batches.releaseFromQc(batch.getId());
    return batch;
  }

  /** Existing fictional Gabardine lots also need explicit width records, beyond their name. */
  private void legacyGabardineWidth() {
    var gabardine =
        catalogue.getActiveCatalogForModule("FABRIC").stream()
            .filter(entry -> SalesQuoteDemoSeeder.PRODUCT_GABARDINE.equals(entry.getProductName()))
            .filter(entry -> entry.getCustomerId() == null)
            .findFirst();
    if (gabardine.isEmpty()) return;
    SalesProductDto entry = gabardine.get();
    ensureWidths(entry.getProductId(), List.of(155));
    for (String code : List.of("LOT-24011", "LOT-24012", "LOT-23087", "LOT-24020", "LOT-23050")) {
      batches
          .findByBatchCode(code)
          .filter(batch -> entry.getProductId().equals(batch.getProductId()))
          .filter(batch -> evidence.finishedWidths(batch.getId()).isEmpty())
          .ifPresent(
              batch ->
                  evidence.recordFinishedWidth(
                      batch.getId(),
                      new LotEvidenceDtos.RecordFinishedWidthRequest(
                          new BigDecimal("155"), "CM", MEASUREMENT_NOTE)));
    }
  }
}
