package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.execution.batch.dto.ReserveRequest;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.masterdata.color.app.ColorService;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.sales.ownership.app.CustomerAccountTeamService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
  @Mock private StockUnitService stockUnitService;
  @Mock private SalesProductService salesProductService;
  @Mock private DiscountPolicyService discountPolicyService;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private QuoteService quoteService;
  @Mock private CustomerAccountTeamService customerAccountTeamService;
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
            stockUnitService,
            salesProductService,
            discountPolicyService,
            exchangeRateService,
            quoteService,
            customerAccountTeamService,
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
    verify(tradingPartnerService, times(1)).createPartner(partnerCaptor.capture(), eq(EMMA_ID));
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

    verify(colorService, times(4)).create(any(), any(), any());
    ArgumentCaptor<ColorCardSpec> colorSpecCaptor = ArgumentCaptor.forClass(ColorCardSpec.class);
    verify(colorService).create(colorSpecCaptor.capture());
    assertThat(colorSpecCaptor.getValue().code()).isEqualTo("PFD-00");
    assertThat(colorSpecCaptor.getValue().colorType()).isEqualTo(ColorType.PFD);
    assertThat(colorSpecCaptor.getValue().colorHex()).isNull();
    assertThat(colorSpecCaptor.getValue().pantoneCode()).isNull();
    assertThat(colorSpecCaptor.getValue().targetLabL()).isNull();
    assertThat(colorSpecCaptor.getValue().deltaETolerance()).isNull();
    verify(productFacade, times(3)).createProduct(any(CreateProductRequest.class));
    verify(salesProductService, times(3)).createEntry(any());

    // Six piece-backed lots are released through an explicit immutable QC decision. The raw-cotton
    // scalar lot has no physical units and remains pending.
    ArgumentCaptor<CreateBatchRequest> batchCaptor =
        ArgumentCaptor.forClass(CreateBatchRequest.class);
    verify(batchService, times(7)).create(batchCaptor.capture());
    verify(batchService, times(6)).releaseFromQc(any(UUID.class));
    // Regression guard: production_execution_batch.chk_batch_unit admits canonical batch units;
    // demo lots currently remain weight-based, while FABRIC purchase lots may use M.
    assertThat(batchCaptor.getAllValues())
        .extracting(CreateBatchRequest::getUnit)
        .allMatch(List.of("KG", "MT", "PIECE", "M")::contains);

    assertThat(batchCaptor.getAllValues())
        .extracting(CreateBatchRequest::getColorId)
        .filteredOn(java.util.Objects::nonNull)
        .hasSize(6);
    assertThat(batchCaptor.getAllValues())
        .filteredOn(request -> "LOT-24040".equals(request.getBatchCode()))
        .singleElement()
        .extracting(CreateBatchRequest::getColorId)
        .isNull();

    // 16 + 24 + 3 + 18 rolls, 48 yarn cartons, 2 waste rolls — all born pending, then graded.
    verify(stockUnitService, times(111))
        .create(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any());
    verify(stockUnitService, times(111)).changeGrade(any(), any(), any(), any());

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
    verify(customerAccountTeamService).addMember(TENANT_ID, partners.get(0).getId(), SANDRA_ID);

    ArgumentCaptor<CreateInternalUserRequest> userCaptor =
        ArgumentCaptor.forClass(CreateInternalUserRequest.class);
    verify(userCreationService, times(1)).createInternalUser(userCaptor.capture());
    assertThat(userCaptor.getValue().isInvitationEmailSuppressed()).isTrue();

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
  void seedFor_propagatesDependencyFailure_soOwnTransactionRollsBack() {
    when(tradingPartnerService.searchByName(TENANT_ID, SalesQuoteDemoSeeder.CUSTOMER_ALBION))
        .thenReturn(List.of());
    when(qualityGradeService.findByProductType(any(ProductType.class)))
        .thenThrow(new IllegalStateException("grades unavailable"));

    // SalesDemoSeeder pattern: failures must ESCAPE seedFor so the REQUIRES_NEW transaction rolls
    // back cleanly; DemoTransactionSeeder catches them outside the boundary. Swallowing inside
    // would commit a rollback-only/aborted transaction and poison the caller's signup transaction.
    assertThatThrownBy(() -> seeder.seedFor(TENANT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("grades unavailable");
    verify(tradingPartnerService, never()).createPartner(any(), any());
    // The finally block must still restore the previous tenant context.
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  void seedFor_declaresItsOwnTransactionBoundary() throws NoSuchMethodException {
    Transactional transactional =
        SalesQuoteDemoSeeder.class
            .getMethod("seedFor", UUID.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).as("seedFor must run in its own transaction").isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }

  private void stubHappyPath() {
    when(tradingPartnerService.searchByName(TENANT_ID, SalesQuoteDemoSeeder.CUSTOMER_ALBION))
        .thenAnswer(invocation -> List.copyOf(partners));
    when(tradingPartnerService.createPartner(any(CreateTradingPartnerRequest.class), eq(EMMA_ID)))
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
    when(colorService.create(any(ColorCardSpec.class)))
        .thenAnswer(
            invocation -> {
              ColorCardSpec spec = invocation.getArgument(0);
              Color colour = Color.create(TENANT_ID, spec);
              colour.setId(UUID.randomUUID());
              return colour;
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
        .thenReturn(Optional.empty());
    when(userRepository.findFirstByTenantIdAndFirstNameAndLastNameAndIsActiveTrue(
            TENANT_ID, "Sandra", "Deal"))
        .thenReturn(Optional.of(user(SANDRA_ID, "Sandra", "Deal")));
    when(organizationService.getRootOrganization())
        .thenReturn(Optional.of(OrganizationDto.builder().id(UUID.randomUUID()).build()));
    when(roleService.findByCode("WORKER"))
        .thenReturn(Optional.of(Role.create("Worker", "WORKER", "Worker")));
    when(departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentCode(
            eq(TENANT_ID), any(UUID.class), eq("SALES")))
        .thenReturn(Optional.empty());
    when(userCreationService.createInternalUser(any(CreateInternalUserRequest.class)))
        .thenReturn(UserDto.builder().id(EMMA_ID).build());
    when(userRepository.findByTenantIdAndId(TENANT_ID, EMMA_ID))
        .thenReturn(Optional.of(user(EMMA_ID, "Emma", "Whitfield")));

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
