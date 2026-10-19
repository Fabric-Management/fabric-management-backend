package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.OfferableStockSummary;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeProductOption;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SOI K03 picker filters and K17 customer-specific visibility. */
@ExtendWith(MockitoExtension.class)
class OrderIntakeProductSearchServiceTest {

  @Mock private ProductSalesDefinitionQueryService productDefinitions;
  @Mock private SalesProductRepository catalogue;
  @Mock private ProposalStockQueryService stockQuery;
  @InjectMocks private OrderIntakeProductSearchService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID customerA = UUID.randomUUID();
  private final UUID customerB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void searchesByNameOrCodeAndHidesOtherCustomersPrivateProducts() {
    ProductDto satin = product("FAB-0142", "Cotton Tencel satin");
    ProductDto privateToB = product("FAB-0999", "Satin special for B");
    ProductDto twill = product("FAB-0200", "Twill");
    when(productDefinitions.sellableProducts(tenantId, ProductType.FABRIC))
        .thenReturn(List.of(satin, privateToB, twill));
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(
            List.of(
                entry(privateToB.getId(), customerB, "9.00"), entry(satin.getId(), null, "4.20")));
    when(productDefinitions.findAll(eq(tenantId), anyList()))
        .thenAnswer(
            invocation -> {
              List<ProductDto> products = invocation.getArgument(1);
              return products.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          ProductDto::getId, OrderIntakeProductSearchServiceTest::definition));
            });
    when(stockQuery.offerableSummaries(eq(tenantId), anyCollection()))
        .thenReturn(
            Map.of(
                satin.getId(),
                new OfferableStockSummary(
                    Map.of(PackageType.ROLL, 2L),
                    new BigDecimal("25.5"),
                    new BigDecimal("100"),
                    1)));

    List<OrderIntakeProductOption> result = service.search(customerA, ProductType.FABRIC, "satin");

    assertThat(result).extracting(OrderIntakeProductOption::uid).containsExactly("FAB-0142");
    assertThat(result.getFirst().listPrice()).isEqualByComparingTo("4.20");
    assertThat(result.getFirst().customerSpecific()).isFalse();
    assertThat(result.getFirst().stock().packages()).containsEntry("ROLL", 2L);
    assertThat(result.getFirst().stock().kg()).isEqualByComparingTo("25.5");
    assertThat(result.getFirst().stock().metres()).isEqualByComparingTo("100");
    assertThat(result.getFirst().stock().unknownPieces()).isEqualTo(1);
    verify(stockQuery).offerableSummaries(tenantId, List.of(satin.getId()));
  }

  @Test
  void aCustomersOwnPrivateProductIsOfferedAndMarkedAsSuch() {
    ProductDto privateToA = product("FAB-0500", "Navy satin for A");
    when(productDefinitions.sellableProducts(tenantId, null)).thenReturn(List.of(privateToA));
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(List.of(entry(privateToA.getId(), customerA, "6.10")));
    when(productDefinitions.findAll(eq(tenantId), anyList()))
        .thenReturn(Map.of(privateToA.getId(), definition(privateToA)));
    when(stockQuery.offerableSummaries(eq(tenantId), anyCollection())).thenReturn(Map.of());

    List<OrderIntakeProductOption> result = service.search(customerA, null, "fab-0500");

    assertThat(result)
        .singleElement()
        .satisfies(
            option -> {
              assertThat(option.customerSpecific()).isTrue();
              assertThat(option.listPrice()).isEqualByComparingTo("6.10");
              assertThat(option.stock().packages()).isEmpty();
              assertThat(option.stock().kg()).isNull();
            });
  }

  @Test
  void fiberFilterIncludesEveryCanonicalSeedFiberPastTheOldFiftyResultCutoff() {
    List<ProductDto> seededFibers =
        java.util.stream.IntStream.rangeClosed(1, 52)
            .mapToObj(
                number ->
                    ProductDto.builder()
                        .id(UUID.randomUUID())
                        .uid("SYS-MAT-" + String.format("%06d", number))
                        .displayName("Fiber " + String.format("%02d", number))
                        .productType(ProductType.FIBER)
                        .unit("KG")
                        .isActive(true)
                        .build())
            .toList();
    when(productDefinitions.sellableProducts(tenantId, ProductType.FIBER)).thenReturn(seededFibers);
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(List.of());
    when(productDefinitions.findAll(eq(tenantId), anyList()))
        .thenAnswer(
            invocation -> {
              List<ProductDto> products = invocation.getArgument(1);
              return products.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          ProductDto::getId, OrderIntakeProductSearchServiceTest::definition));
            });
    when(stockQuery.offerableSummaries(eq(tenantId), anyCollection())).thenReturn(Map.of());

    List<OrderIntakeProductOption> result = service.search(customerA, ProductType.FIBER, null);

    assertThat(result).hasSize(52);
    assertThat(result)
        .extracting(OrderIntakeProductOption::uid)
        .contains("SYS-MAT-000001", "SYS-MAT-000052");
  }

  @Test
  void nonSellableTypesReturnNothing() {
    assertThat(service.search(customerA, ProductType.CHEMICAL, null)).isEmpty();
    verify(productDefinitions, never()).sellableProducts(eq(tenantId), eq(ProductType.CHEMICAL));
    verify(stockQuery, never()).offerableSummaries(eq(tenantId), anyCollection());
  }

  @Test
  void unnamedFabricIsSearchableByItsVisibleCatalogueNameAndKeepsItsWidths() {
    ProductDto fabric = product("SYS-000-PROD-F3401FC5", "SYS-000-PROD-F3401FC5");
    SalesProduct named = entry(fabric.getId(), null, "8.60");
    named.setProductName("Cotton Poplin 120gsm");
    stubSingleProduct(fabric, List.of(named));

    var result = service.search(customerA, ProductType.FABRIC, "poplin");

    assertThat(result)
        .singleElement()
        .satisfies(
            option -> {
              assertThat(option.displayName()).isEqualTo("Cotton Poplin 120gsm");
              assertThat(option.uid()).isEqualTo("SYS-000-PROD-F3401FC5");
              assertThat(option.finishedWidths())
                  .containsExactly(
                      new ProductSalesDefinitionDto.FinishedWidthOption(
                          new BigDecimal("160.00"), "CM"));
            });
  }

  @Test
  void catalogueFallbackNeverSearchesOrRevealsAnotherCustomersPrivateName() {
    ProductDto fabric = product("FAB-GENERIC", "FAB-GENERIC");
    SalesProduct general = entry(fabric.getId(), null, "8.60");
    general.setProductName("General Poplin");
    SalesProduct privateName = entry(fabric.getId(), customerB, "7.80");
    privateName.setProductName("Confidential customer B design");
    stubSingleProduct(fabric, List.of(privateName, general));

    assertThat(service.search(customerA, ProductType.FABRIC, "confidential")).isEmpty();
    assertThat(service.search(customerA, ProductType.FABRIC, "poplin"))
        .extracting(OrderIntakeProductOption::displayName)
        .containsExactly("General Poplin");
    assertThat(service.search(customerB, ProductType.FABRIC, "confidential"))
        .extracting(OrderIntakeProductOption::displayName)
        .containsExactly("Confidential customer B design");
  }

  @Test
  void realMasterDataNameWinsOverCatalogueSnapshot() {
    ProductDto fabric = product("FAB-POPLIN", "Current Poplin Definition");
    SalesProduct older = entry(fabric.getId(), null, "8.60");
    older.setProductName("Older catalogue name");
    stubSingleProduct(fabric, List.of(older));

    assertThat(service.search(customerA, ProductType.FABRIC, "current"))
        .extracting(OrderIntakeProductOption::displayName)
        .containsExactly("Current Poplin Definition");
  }

  @Test
  void aCustomersUnnamedPrivateEntryFallsBackToTheGeneralCatalogueName() {
    ProductDto fabric = product("SYS-000-PROD-0A1B2C3D", "SYS-000-PROD-0A1B2C3D");
    SalesProduct privateWithoutName = entry(fabric.getId(), customerA, "7.80");
    SalesProduct general = entry(fabric.getId(), null, "8.60");
    general.setProductName("Cotton Poplin 120gsm");
    stubSingleProduct(fabric, List.of(privateWithoutName, general));

    assertThat(service.search(customerA, ProductType.FABRIC, null))
        .singleElement()
        .satisfies(
            option -> {
              assertThat(option.displayName()).isEqualTo("Cotton Poplin 120gsm");
              assertThat(option.customerSpecific()).isTrue();
              assertThat(option.listPrice()).isEqualByComparingTo("7.80");
            });
  }

  @Test
  void theProductsDescriptionIsOfferedWithIt() {
    ProductDto described = product("FAB-POPLIN", "Cotton Poplin 120gsm");
    described.setDescription("Plain-weave cotton for shirts.");
    stubSingleProduct(described, List.of(entry(described.getId(), null, "8.60")));

    assertThat(service.search(customerA, ProductType.FABRIC, null))
        .extracting(OrderIntakeProductOption::description)
        .containsExactly("Plain-weave cotton for shirts.");
  }

  @Test
  void anUndescribedProductHasNoDescription() {
    ProductDto plain = product("FAB-PLAIN", "Cotton Drill 240gsm");
    stubSingleProduct(plain, List.of(entry(plain.getId(), null, "8.60")));

    assertThat(service.search(customerA, ProductType.FABRIC, null))
        .extracting(OrderIntakeProductOption::description)
        .containsExactly((String) null);
  }

  private void stubSingleProduct(ProductDto product, List<SalesProduct> entries) {
    when(productDefinitions.sellableProducts(tenantId, ProductType.FABRIC))
        .thenReturn(List.of(product));
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(entries);
    when(productDefinitions.findAll(eq(tenantId), anyList()))
        .thenAnswer(
            invocation -> {
              List<ProductDto> selected = invocation.getArgument(1);
              return selected.isEmpty() ? Map.of() : Map.of(product.getId(), definition(product));
            });
    when(stockQuery.offerableSummaries(eq(tenantId), anyCollection())).thenReturn(Map.of());
  }

  private static ProductDto product(String uid, String name) {
    return ProductDto.builder()
        .id(UUID.randomUUID())
        .uid(uid)
        .displayName(name)
        .productType(ProductType.FABRIC)
        .unit("M")
        .isActive(true)
        .build();
  }

  private static ProductSalesDefinitionDto definition(ProductDto product) {
    return new ProductSalesDefinitionDto(
        product.getId(),
        product.getUid(),
        product.getDisplayName(),
        product.getProductType(),
        product.getUnit(),
        true,
        List.of(),
        product.getProductType() == ProductType.FABRIC
            ? List.of(
                new ProductSalesDefinitionDto.FinishedWidthOption(new BigDecimal("160.00"), "CM"))
            : List.of());
  }

  private static SalesProduct entry(UUID productId, UUID customerId, String price) {
    SalesProduct entry = new SalesProduct();
    entry.setProductId(productId);
    entry.setCustomerId(customerId);
    entry.setListPrice(new BigDecimal(price));
    entry.setCurrency("EUR");
    return entry;
  }
}
