package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SOI S01, S02, S14 (catalogue visibility) and S20, plus the TK-2 duplicate rule. */
@ExtendWith(MockitoExtension.class)
class CatalogLineValidatorTest {

  @Mock private ProductSalesDefinitionQueryService productDefinitions;
  @Mock private SalesProductRepository catalogue;
  @Mock private ColorQueryService colors;
  @InjectMocks private CatalogLineValidator validator;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID customerA = UUID.randomUUID();
  private final UUID customerB = UUID.randomUUID();
  private final UUID satin = UUID.randomUUID();
  private final UUID navy = UUID.randomUUID();
  private final UUID ecru = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    lenient()
        .when(productDefinitions.find(tenantId, satin))
        .thenReturn(Optional.of(fabric(satin, true)));
    lenient()
        .when(
            catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(List.of());
    lenient()
        .when(colors.findActiveReferenceById(navy))
        .thenReturn(
            Optional.of(new ColorQueryService.ColorReference(navy, "NV", "Navy", "#001", true)));
    lenient()
        .when(colors.findActiveReferenceById(ecru))
        .thenReturn(
            Optional.of(new ColorQueryService.ColorReference(ecru, "EC", "Ecru", "#eee", true)));
  }

  @Test
  void s01_aDescriptionNeverReplacesTheProduct() {
    SalesOrderLineRequest line = line(null, "M", navy, "160", "CM");
    line.setProductDesc("navy satin like the swatch");

    assertCode(() -> validate(line), "ORDER_INTAKE_PRODUCT_REQUIRED");
  }

  @Test
  void s02_oneProductCarriesSeveralDistributionsWithTheirOwnColourAndWidth() {
    assertThatCode(
            () ->
                validator.validate(
                    tenantId,
                    customerA,
                    List.of(
                        line(satin, "M", navy, "160", "CM"), line(satin, "M", ecru, "150", "CM"))))
        .doesNotThrowAnyException();
  }

  @Test
  void tk2_theSameDistributionTwiceIsRejected() {
    assertCode(
        () ->
            validator.validate(
                tenantId,
                customerA,
                List.of(
                    line(satin, "M", navy, "160", "CM"), line(satin, "m", navy, "160.00", "cm"))),
        "ORDER_INTAKE_DUPLICATE_DISTRIBUTION");
  }

  @Test
  void tk2_aDifferentDeliveryDateIsASeparateDistribution() {
    SalesOrderLineRequest later = line(satin, "M", navy, "160", "CM");
    later.setRequestedDeliveryDate(LocalDate.of(2026, 12, 1));

    assertThatCode(
            () ->
                validator.validate(
                    tenantId, customerA, List.of(line(satin, "M", navy, "160", "CM"), later)))
        .doesNotThrowAnyException();
  }

  @Test
  void unknownOrInactiveProductsAreRefused() {
    UUID unknown = UUID.randomUUID();
    when(productDefinitions.find(tenantId, unknown)).thenReturn(Optional.empty());
    UUID inactive = UUID.randomUUID();
    when(productDefinitions.find(tenantId, inactive))
        .thenReturn(Optional.of(fabric(inactive, false)));

    assertCode(
        () -> validate(line(unknown, "M", null, "160", "CM")),
        "ORDER_INTAKE_PRODUCT_NOT_AVAILABLE");
    assertCode(
        () -> validate(line(inactive, "M", null, "160", "CM")),
        "ORDER_INTAKE_PRODUCT_NOT_AVAILABLE");
  }

  @Test
  void onlyFibreYarnAndFabricAreOrderable() {
    UUID chemical = UUID.randomUUID();
    when(productDefinitions.find(tenantId, chemical))
        .thenReturn(
            Optional.of(
                new ProductSalesDefinitionDto(
                    chemical,
                    "CHEM-1",
                    "Dye",
                    ProductType.CHEMICAL,
                    "KG",
                    true,
                    List.of(),
                    List.of())));

    assertCode(
        () -> validate(line(chemical, "KG", null, null, null)),
        "ORDER_INTAKE_PRODUCT_TYPE_NOT_SELLABLE");
  }

  @Test
  void s14_aProductPrivateToAnotherCustomerIsNotOrderable() {
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(List.of(entry(satin, customerB)));

    assertCode(
        () -> validate(line(satin, "M", navy, "160", "CM")), "ORDER_INTAKE_PRODUCT_NOT_VISIBLE");
  }

  @Test
  void s14_aProductPrivateToThisCustomerOrInTheGeneralCatalogueIsOrderable() {
    when(catalogue.findAllByTenantIdAndProductIdInAndIsActiveTrue(eq(tenantId), anyCollection()))
        .thenReturn(List.of(entry(satin, customerA)))
        .thenReturn(List.of(entry(satin, customerB), entry(satin, null)));

    assertThatCode(() -> validate(line(satin, "M", navy, "160", "CM"))).doesNotThrowAnyException();
    assertThatCode(() -> validate(line(satin, "M", navy, "160", "CM"))).doesNotThrowAnyException();
  }

  @Test
  void s20_metreAndKilogramAreNotInterchangeable() {
    assertCode(
        () -> validate(line(satin, "KG", navy, "160", "CM")), "ORDER_INTAKE_UNIT_NOT_ALLOWED");
  }

  @Test
  void s20_anExtraSalesUnitOfTheProductIsAllowed() {
    when(productDefinitions.find(tenantId, satin))
        .thenReturn(
            Optional.of(
                new ProductSalesDefinitionDto(
                    satin,
                    "FAB-1",
                    "Satin",
                    ProductType.FABRIC,
                    "M",
                    true,
                    List.of("YD"),
                    List.of(
                        new ProductSalesDefinitionDto.FinishedWidthOption(
                            new BigDecimal("160.00"), "CM")))));

    assertThatCode(() -> validate(line(satin, "yd", navy, "160", "CM"))).doesNotThrowAnyException();
  }

  @Test
  void colourMustBeAnActiveColourCard() {
    UUID unknownColour = UUID.randomUUID();
    when(colors.findActiveReferenceById(unknownColour)).thenReturn(Optional.empty());

    assertCode(
        () -> validate(line(satin, "M", unknownColour, "160", "CM")),
        "ORDER_INTAKE_COLOR_NOT_DEFINED");
  }

  @Test
  void k05_widthMustBeADefinedFinishedWidth() {
    assertCode(
        () -> validate(line(satin, "M", navy, "155", "CM")), "ORDER_INTAKE_WIDTH_NOT_DEFINED");
    assertCode(() -> validate(line(satin, "M", navy, null, null)), "ORDER_INTAKE_WIDTH_REQUIRED");
    assertCode(
        () -> validate(line(satin, "M", navy, "160", "IN")), "ORDER_INTAKE_WIDTH_NOT_DEFINED");
  }

  @Test
  void aProductWithoutWidthsRefusesAWidth() {
    UUID yarn = UUID.randomUUID();
    when(productDefinitions.find(tenantId, yarn))
        .thenReturn(
            Optional.of(
                new ProductSalesDefinitionDto(
                    yarn, "YRN-1", "Ne 30/1", ProductType.YARN, "KG", true, List.of(), List.of())));

    assertThatCode(() -> validate(line(yarn, "KG", null, null, null))).doesNotThrowAnyException();
    assertCode(
        () -> validate(line(yarn, "KG", null, "160", "CM")), "ORDER_INTAKE_WIDTH_NOT_DEFINED");
  }

  private void validate(SalesOrderLineRequest line) {
    validator.validate(tenantId, customerA, List.of(line));
  }

  private static void assertCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo(code);
  }

  private static SalesOrderLineRequest line(
      UUID productId, String unit, UUID colorId, String width, String widthUnit) {
    return SalesOrderLineRequest.builder()
        .productId(productId)
        .requestedQty(new BigDecimal("500"))
        .unit(unit)
        .colorId(colorId)
        .finishedWidth(width == null ? null : new BigDecimal(width))
        .finishedWidthUnit(widthUnit)
        .build();
  }

  private static ProductSalesDefinitionDto fabric(UUID productId, boolean active) {
    return new ProductSalesDefinitionDto(
        productId,
        "FAB-1",
        "Cotton Tencel satin",
        ProductType.FABRIC,
        "M",
        active,
        List.of(),
        List.of(
            new ProductSalesDefinitionDto.FinishedWidthOption(new BigDecimal("150.00"), "CM"),
            new ProductSalesDefinitionDto.FinishedWidthOption(new BigDecimal("160.00"), "CM")));
  }

  private static SalesProduct entry(UUID productId, UUID customerId) {
    SalesProduct entry = new SalesProduct();
    entry.setProductId(productId);
    entry.setCustomerId(customerId);
    return entry;
  }
}
