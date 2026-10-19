package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.bootstrap.FabricStockDemoPlan.Fabric;
import com.fabricmanagement.common.infrastructure.bootstrap.FabricStockDemoPlan.Lot;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named, coloured fabric examples installed inside the trusted sales playground seed transaction.
 * What is stocked (fabrics, colours, widths, lots and the length of every roll) is the data in
 * {@link FabricStockDemoPlan}; this class only writes it. Product width options and synthetic lot
 * measurements are separate records; a name is never parsed as measurement evidence. No startup,
 * login, reset or ordinary tenant invokes this helper.
 */
@Component
@RequiredArgsConstructor
public class FabricStockDemoSeeder {

  /** The finished widths every demo fabric is offered in. */
  static final List<Integer> WIDTHS_CM = List.of(150, 160);

  static final String MEASUREMENT_NOTE =
      "Playground example: synthetic finished-width measurement, not a real inspection.";

  /** Rolls shorter than this were cut where a fabric fault was removed, not left as a remainder. */
  private static final int CUT_ROLL_BELOW_M = 90;

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
    Map<String, UUID> colourIds = colourIds();
    for (Fabric fabric : FabricStockDemoPlan.FABRICS) {
      ProductDto product = product(fabric);
      ensureWidths(product.getId(), WIDTHS_CM);
      Map<String, List<UUID>> combinable = new LinkedHashMap<>();
      for (Lot lot : fabric.lots()) {
        BatchDto batch = lot(fabric, product, colourIds, grade, lot);
        if (lot.mixable()) {
          String group = lot.colour() + " at " + lot.widthCm() + " cm";
          combinable.computeIfAbsent(group, key -> new ArrayList<>()).add(batch.getId());
        }
      }
      combinable.forEach(this::confirmCombinable);
    }
    legacyGabardineWidth();
  }

  private Map<String, UUID> colourIds() {
    Map<String, UUID> ids = new HashMap<>();
    for (var card : colours.list(true)) {
      ids.putIfAbsent(card.getCode(), card.getId());
    }
    return ids;
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

  /** One dye lot with its rolls, released from QC; an existing lot is left as it is. */
  private BatchDto lot(
      Fabric fabric, ProductDto product, Map<String, UUID> colourIds, UUID grade, Lot lot) {
    UUID colour = colourIds.get(lot.colour());
    if (colour == null) {
      throw new IllegalStateException("Demo colour missing: " + lot.colour());
    }
    String code = FabricStockDemoPlan.lotCode(fabric, lot);
    var existing = batches.findByBatchCode(code);
    if (existing.isPresent()) return existing.get();
    BatchDto batch =
        batches.create(
            CreateBatchRequest.builder()
                .productId(product.getId())
                .productType(ProductType.FABRIC)
                .colorId(colour)
                .batchCode(code)
                .quantity(lot.metresDecimal())
                .unit("M")
                .sourceType(BatchSourceType.INITIAL_STOCK)
                .remarks(remarks(lot))
                .build());
    evidence.recordFinishedWidth(
        batch.getId(),
        new LotEvidenceDtos.RecordFinishedWidthRequest(
            BigDecimal.valueOf(lot.widthCm()), "CM", MEASUREMENT_NOTE));
    for (int index = 0; index < lot.rolls().size(); index++) {
      BigDecimal length = BigDecimal.valueOf(lot.rolls().get(index));
      var piece =
          pieces.create(
              batch.getId(),
              ProductType.FABRIC,
              code + "-" + String.format("%02d", index + 1),
              null,
              PackageType.ROLL,
              weightKg(length, lot.widthCm(), fabric.gsm()),
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

  /** Roll weight in kg: metres x width in cm x gsm, as 1 m x 1 cm of 1 gsm weighs 0.00001 kg. */
  static BigDecimal weightKg(BigDecimal lengthM, int widthCm, int gsm) {
    return lengthM
        .multiply(BigDecimal.valueOf(widthCm))
        .multiply(BigDecimal.valueOf(gsm))
        .divide(new BigDecimal("100000"), 3, RoundingMode.HALF_UP);
  }

  private static String remarks(Lot lot) {
    long remainders =
        lot.rolls().stream()
            .filter(length -> length != FabricStockDemoPlan.ROLL_M && length >= CUT_ROLL_BELOW_M)
            .count();
    StringBuilder text =
        new StringBuilder("Illustrative playground stock; ")
            .append(lot.widthCm())
            .append(" cm finished width; ")
            .append(lot.rolls().size())
            .append(" rolls.");
    if (remainders > 0) {
      text.append(" Includes the end-of-lot remainder left after winding ")
          .append(FabricStockDemoPlan.ROLL_M)
          .append(" m rolls.");
    }
    if (lot.cutRolls() > 0) {
      text.append(" Includes a roll cut short where a fabric fault was removed in production.");
    }
    if (!lot.mixable()) {
      text.append(" Shade not yet confirmed against the other lots of this colour.");
    }
    return text.toString();
  }

  /** Lots of one colour and width that may be shipped together, confirmed for every customer. */
  private void confirmCombinable(String group, List<UUID> ids) {
    if (ids.size() < 2) return;
    if (evidence.compatibilityFor(ids.getFirst()).stream()
        .anyMatch(
            confirmation ->
                confirmation.customerId() == null && confirmation.batchIds().containsAll(ids))) {
      return;
    }
    evidence.confirmCompatibility(
        new LotEvidenceDtos.ConfirmCompatibilityRequest(
            ids, null, "Synthetic playground example: the lots of " + group + " may be combined."));
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
