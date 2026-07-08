package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.production.execution.batch.app.BatchAttributeService;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.execution.batch.dto.ReserveRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesQuoteDemoSeederTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID EMMA_ID = UUID.randomUUID();
  private static final UUID SANDRA_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 8);

  @Mock private TradingPartnerService tradingPartnerService;
  @Mock private ProductFacade productFacade;
  @Mock private QualityGradeService qualityGradeService;
  @Mock private ColorService colorService;
  @Mock private BatchService batchService;
  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitService stockUnitService;
  @Mock private BatchAttributeService batchAttributeService;
  @Mock private ProductAttributeRepository productAttributeRepository;
  @Mock private SalesProductService salesProductService;
  @Mock private DiscountPolicyService discountPolicyService;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private QuoteService quoteService;
  @Mock private UserRepository userRepository;
  @Mock private UserCreationService userCreationService;
  @Mock private RoleService roleService;
  @Mock private DepartmentRepository departmentRepository;
  @Mock private OrganizationService organizationService;

  private final List<TradingPartnerDto> partners = new ArrayList<>();
  private final Map<String, UUID> batchIdsByCode = new HashMap<>();

  private SalesQuoteDemoSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), ZoneId.of("UTC"));
    seeder =
        new SalesQuoteDemoSeeder(
            tradingPartnerService,
            productFacade,
            qualityGradeService,
            colorService,
            batchService,
            batchRepository,
            stockUnitService,
            batchAttributeService,
            productAttributeRepository,
            salesProductService,
            discountPolicyService,
            exchangeRateService,
            quoteService,
            userRepository,
            userCreationService,
            roleService,
            departmentRepository,
            organizationService,
            clock);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void seedFor_createsSalesAtpDatasetAndIsIdempotent() {
    stubHappyPath();

    seeder.seedFor(TENANT_ID);
    seeder.seedFor(TENANT_ID);

    // Idempotency: the second run must short-circuit on the demo-customer marker.
    ArgumentCaptor<CreateTradingPartnerRequest> partnerCaptor =
        ArgumentCaptor.forClass(CreateTradingPartnerRequest.class);
    verify(tradingPartnerService, times(1)).createPartner(partnerCaptor.capture());
    assertThat(partnerCaptor.getValue().getPartnerType()).isEqualTo(PartnerType.CUSTOMER);
    assertThat(partnerCaptor.getValue().getCountry()).isEqualTo("GBR");

    // 3 grades per product type (FABRIC, YARN, FIBER); Waste is never saleable.
    ArgumentCaptor<Boolean> saleableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(qualityGradeService, times(9))
        .create(
            any(ProductType.class),
            any(String.class),
            any(String.class),
            anyInt(),
            any(BigDecimal.class),
            saleableCaptor.capture(),
            anyBoolean(),
            any(),
            anyBoolean());
    assertThat(saleableCaptor.getAllValues()).containsSequence(true, true, false);

    verify(colorService, times(5)).create(any(), any(), any());
    verify(productFacade, times(3)).createProduct(any(CreateProductRequest.class));
    verify(salesProductService, times(3)).createEntry(any());

    // 6 scenario lots + 1 waste-grade negative-test lot; all released from QC.
    verify(batchService, times(7)).create(any(CreateBatchRequest.class));
    verify(batchRepository, times(7)).save(any(Batch.class));

    // 16 + 24 + 3 rolls, 48 yarn cartons, 2 waste rolls — each graded through the service.
    verify(stockUnitService, times(93))
        .create(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any());
    verify(stockUnitService, times(93)).changeGrade(any(), any(), any(), any());

    // Emma's open quote + the demo user's draft quote.
    ArgumentCaptor<QuoteCreateRequest> quoteCaptor =
        ArgumentCaptor.forClass(QuoteCreateRequest.class);
    verify(quoteService, times(2)).createQuote(quoteCaptor.capture());
    QuoteCreateRequest emmaQuote = quoteCaptor.getAllValues().get(0);
    // Quote numbers carry a per-tenant suffix: quote_number is globally unique in the schema.
    assertThat(emmaQuote.getQuoteNumber())
        .startsWith(SalesQuoteDemoSeeder.COMPETING_QUOTE_NUMBER_STEM);
    assertThat(emmaQuote.getCurrency()).isEqualTo("GBP");
    assertThat(emmaQuote.getAssignedToId()).isEqualTo(EMMA_ID);
    assertThat(emmaQuote.getValidUntil()).isEqualTo(TODAY.plusDays(14));
    QuoteCreateRequest draftQuote = quoteCaptor.getAllValues().get(1);
    assertThat(draftQuote.getQuoteNumber())
        .startsWith(SalesQuoteDemoSeeder.DRAFT_QUOTE_NUMBER_STEM);
    assertThat(draftQuote.getAssignedToId()).isEqualTo(SANDRA_ID);

    // Intents ride through QuoteService.addQuoteLine: per line, the selected-lot quantity must
    // equal the requested quantity so BatchLotQuantityIntentPort invariants hold.
    ArgumentCaptor<AddQuoteLineRequest> lineCaptor =
        ArgumentCaptor.forClass(AddQuoteLineRequest.class);
    verify(quoteService, times(3)).addQuoteLine(any(UUID.class), lineCaptor.capture());
    List<AddQuoteLineRequest> lines = lineCaptor.getAllValues();

    List<AddQuoteLineRequest> lotBackedLines =
        lines.stream().filter(line -> line.getSelectedLots() != null).toList();
    assertThat(lotBackedLines).hasSize(2);
    assertThat(lotBackedLines)
        .allSatisfy(
            line -> {
              assertThat(line.getSelectedLots()).hasSize(1);
              QuoteLineLotSelectionRequest selection = line.getSelectedLots().get(0);
              assertThat(selection.quantity()).isEqualByComparingTo(line.getRequestedQty());
            });
    assertThat(lotBackedLines.get(0).getSelectedLots().get(0).lotId())
        .isEqualTo(batchIdsByCode.get("LOT-24011"));
    assertThat(lotBackedLines.get(0).getRequestedQty()).isEqualByComparingTo("1500");
    assertThat(lotBackedLines.get(1).getSelectedLots().get(0).lotId())
        .isEqualTo(batchIdsByCode.get("LOT-24012"));
    assertThat(lotBackedLines.get(1).getRequestedQty()).isEqualByComparingTo("2000");

    // The draft line stays free-entry: no lots selected.
    assertThat(lines.stream().filter(line -> line.getSelectedLots() == null)).hasSize(1);

    // Hard reservation: 200 m on the Ecru bulk lot, seeded through BatchService.reserve.
    ArgumentCaptor<ReserveRequest> reserveCaptor = ArgumentCaptor.forClass(ReserveRequest.class);
    verify(batchService, times(1))
        .reserve(eq(batchIdsByCode.get("LOT-24020")), reserveCaptor.capture());
    assertThat(reserveCaptor.getValue().getQuantity()).isEqualByComparingTo("200");
    assertThat(reserveCaptor.getValue().getReferenceType()).isEqualTo("SALES_ORDER");
  }

  @Test
  void seedFor_neverThrowsWhenDependencyFails() {
    when(tradingPartnerService.searchByName(TENANT_ID, SalesQuoteDemoSeeder.CUSTOMER_ALBION))
        .thenReturn(List.of());
    when(qualityGradeService.findByProductType(any(ProductType.class)))
        .thenThrow(new IllegalStateException("grades unavailable"));

    assertThatCode(() -> seeder.seedFor(TENANT_ID)).doesNotThrowAnyException();
    verify(tradingPartnerService, never()).createPartner(any());
  }

  private void stubHappyPath() {
    when(tradingPartnerService.searchByName(TENANT_ID, SalesQuoteDemoSeeder.CUSTOMER_ALBION))
        .thenAnswer(invocation -> List.copyOf(partners));
    when(tradingPartnerService.createPartner(any(CreateTradingPartnerRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateTradingPartnerRequest req = invocation.getArgument(0);
              TradingPartnerDto dto =
                  TradingPartnerDto.builder()
                      .id(UUID.randomUUID())
                      .displayName(req.getCompanyName())
                      .partnerType(req.getPartnerType())
                      .build();
              partners.add(dto);
              return dto;
            });

    when(qualityGradeService.findByProductType(any(ProductType.class))).thenReturn(List.of());
    when(qualityGradeService.create(
            any(ProductType.class),
            any(String.class),
            any(String.class),
            anyInt(),
            any(BigDecimal.class),
            anyBoolean(),
            anyBoolean(),
            any(),
            anyBoolean()))
        .thenAnswer(
            invocation -> {
              QualityGrade grade =
                  QualityGrade.builder()
                      .productType(invocation.getArgument(0))
                      .code(invocation.getArgument(1))
                      .name(invocation.getArgument(2))
                      .rank(invocation.getArgument(3))
                      .priceFactor(invocation.getArgument(4))
                      .saleable(invocation.getArgument(5))
                      .requiresApproval(invocation.getArgument(6))
                      .colorHex(invocation.getArgument(7))
                      .isDefault(invocation.getArgument(8))
                      .build();
              grade.setId(UUID.randomUUID());
              grade.setTenantId(TENANT_ID);
              return grade;
            });

    when(colorService.list(true)).thenReturn(List.of());
    when(colorService.create(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Color colour =
                  Color.builder()
                      .code(invocation.getArgument(0))
                      .name(invocation.getArgument(1))
                      .colorHex(invocation.getArgument(2))
                      .build();
              colour.setId(UUID.randomUUID());
              colour.setTenantId(TENANT_ID);
              return colour;
            });

    when(productAttributeRepository.findByAttributeCode("COLOR")).thenReturn(Optional.empty());
    when(productAttributeRepository.save(any(ProductAttribute.class)))
        .thenAnswer(
            invocation -> {
              ProductAttribute attribute = invocation.getArgument(0);
              attribute.setId(UUID.randomUUID());
              return attribute;
            });

    when(productFacade.createProduct(any(CreateProductRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateProductRequest req = invocation.getArgument(0);
              return ProductDto.builder()
                  .id(UUID.randomUUID())
                  .productType(req.getProductType())
                  .unit(req.getUnit())
                  .build();
            });

    when(discountPolicyService.getActivePolicy("FABRIC"))
        .thenThrow(new IllegalArgumentException("No active discount policy"));

    when(batchService.create(any(CreateBatchRequest.class)))
        .thenAnswer(
            invocation -> {
              CreateBatchRequest req = invocation.getArgument(0);
              UUID id = UUID.randomUUID();
              batchIdsByCode.put(req.getBatchCode(), id);
              return BatchDto.builder()
                  .id(id)
                  .batchCode(req.getBatchCode())
                  .productType(req.getProductType())
                  .build();
            });
    when(batchRepository.findByIdAndTenantId(any(UUID.class), eq(TENANT_ID)))
        .thenAnswer(
            invocation -> {
              Batch batch =
                  Batch.builder()
                      .productId(UUID.randomUUID())
                      .productType(ProductType.FABRIC)
                      .batchCode("LOT-TEST")
                      .quantity(BigDecimal.ONE)
                      .unit("M")
                      .status(BatchStatus.PENDING_QC)
                      .build();
              batch.setId(invocation.getArgument(0));
              batch.setTenantId(TENANT_ID);
              return Optional.of(batch);
            });
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(stockUnitService.create(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any()))
        .thenAnswer(
            invocation -> {
              StockUnit unit = StockUnit.builder().barcode(invocation.getArgument(2)).build();
              unit.setId(UUID.randomUUID());
              return unit;
            });

    when(userRepository.findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(
            TENANT_ID,
            SalesQuoteDemoSeeder.MARKETER_FIRST_NAME,
            SalesQuoteDemoSeeder.MARKETER_LAST_NAME))
        .thenReturn(Optional.of(user(EMMA_ID, "Emma", "Whitfield")));
    when(userRepository.findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(
            TENANT_ID, "Sandra", "Deal"))
        .thenReturn(Optional.of(user(SANDRA_ID, "Sandra", "Deal")));

    when(quoteService.createQuote(any(QuoteCreateRequest.class)))
        .thenAnswer(
            invocation -> {
              QuoteCreateRequest req = invocation.getArgument(0);
              Quote quote = req.toQuote();
              quote.setId(UUID.randomUUID());
              quote.setTenantId(TENANT_ID);
              return quote;
            });
  }

  private User user(UUID id, String firstName, String lastName) {
    User user = User.builder().firstName(firstName).lastName(lastName).build();
    user.setId(id);
    return user;
  }
}
