package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderCurrencyTotalDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class SalesOrderServiceCreateTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TradingPartnerService partnerService;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private SalesOrderRuleEngine ruleEngine;
  @Mock private ModuleSpecsValidator moduleSpecsValidator;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private ApprovalPort approvalPort;

  @Mock
  private com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverActivationRepository
      orderCoverActivationRepository;

  @Mock private OrderCoverEnrolmentService orderCoverEnrolmentService;
  @Mock private OrderIntakeHooks orderIntakeHooks;

  @Mock
  private com.fabricmanagement.sales.orderintake.app.CustomerRequestService customerRequestService;

  @Mock private DeliveryCommitmentService deliveryCommitments;
  @InjectMocks private SalesOrderService salesOrderService;

  @Captor private ArgumentCaptor<SalesOrder> orderCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID requestPartnerId = UUID.randomUUID();
  private final UUID tradingPartnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createOrder_totalsEachAgreedCurrencyFromItsOwnLinesAndNeverAddsThem() {
    CreateSalesOrderRequest request = baseRequest();
    request.setPaymentTerms("30% advance, balance against B/L copy");
    SalesOrderLineRequest lira = lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY");
    lira.setDiscountAmount(new BigDecimal("2"));
    lira.setTaxAmount(new BigDecimal("5"));
    request.setLines(
        List.of(lira, lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "USD")));
    stubSuccessfulCreate();
    echoSavedLines();

    SalesOrderDto created = salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getPaymentTerms())
        .isEqualTo("30% advance, balance against B/L copy");
    assertThat(created.getTotals())
        .extracting(
            SalesOrderCurrencyTotalDto::currency,
            SalesOrderCurrencyTotalDto::subtotal,
            SalesOrderCurrencyTotalDto::discountAmount,
            SalesOrderCurrencyTotalDto::taxAmount,
            SalesOrderCurrencyTotalDto::grandTotal)
        .usingRecursiveFieldByFieldElementComparator(
            org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration.builder()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .build())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "TRY",
                new BigDecimal("30"),
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("33")),
            org.assertj.core.groups.Tuple.tuple(
                "USD",
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10")));
    assertThat(created.getLines())
        .extracting(line -> line.getCurrency())
        .containsExactly("TRY", "USD");
  }

  @Test
  void createOrderTakesTenantActivationLockBeforePersistingTheOrder() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of());
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    var ordered = org.mockito.Mockito.inOrder(orderCoverActivationRepository, orderRepository);
    ordered.verify(orderCoverActivationRepository).lockForOrderInsert(tenantId);
    ordered.verify(orderRepository).save(any(SalesOrder.class));
  }

  @Test
  void createOrder_answersWithTheFourDecimalPriceAsAgreed() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of(lineRequest(new BigDecimal("1000"), new BigDecimal("1.2345"), "USD")));
    stubSuccessfulCreate();
    echoSavedLines();

    SalesOrderDto created = salesOrderService.createOrder(request);

    // The edit screen saves this value back; a rounded 1.23 would silently change the price.
    assertThat(created.getLines().getFirst().getUnitPrice()).isEqualByComparingTo("1.2345");
    assertThat(created.getTotals().getFirst().subtotal()).isEqualByComparingTo("1234.50");
  }

  @Test
  void createOrder_recordsEachLinesToleranceWithItsRecorderAndTimeAndWhereTheOrderWasAgreed() {
    UUID actor = UUID.randomUUID();
    TenantContext.setCurrentUserId(actor);
    CreateSalesOrderRequest request = baseRequest();
    request.setAgreementContext(
        com.fabricmanagement.sales.salesorder.domain.AgreementContext.WE_VISITED_CUSTOMER);
    request.setContactName(" Ayşe Demir ");
    request.setContactPhone("+905551112233");
    request.setContactWhatsapp(true);
    SalesOrderLineRequest standard = lineRequest(new BigDecimal("500"), BigDecimal.TEN, "USD");
    standard.setToleranceUpPct(new BigDecimal("5"));
    standard.setToleranceDownPct(new BigDecimal("5"));
    SalesOrderLineRequest exact = lineRequest(new BigDecimal("200"), BigDecimal.TEN, "USD");
    request.setLines(List.of(standard, exact));
    stubSuccessfulCreate();
    echoSavedLines();

    SalesOrderDto created = salesOrderService.createOrder(request);

    var withTolerance = created.getLines().getFirst();
    assertThat(withTolerance.getToleranceUpPct()).isEqualByComparingTo("5");
    assertThat(withTolerance.getToleranceDownPct()).isEqualByComparingTo("5");
    assertThat(withTolerance.getToleranceRecordedBy()).isEqualTo(actor);
    assertThat(withTolerance.getToleranceRecordedAt()).isNotNull();
    var without = created.getLines().get(1);
    assertThat(without.getToleranceUpPct()).isNull();
    assertThat(without.getToleranceRecordedBy()).isNull();
    assertThat(created.getAgreementContext())
        .isEqualTo(
            com.fabricmanagement.sales.salesorder.domain.AgreementContext.WE_VISITED_CUSTOMER);
    assertThat(created.getContactName()).isEqualTo("Ayşe Demir");
    assertThat(created.getContactPhone()).isEqualTo("+905551112233");
    assertThat(created.isContactWhatsapp()).isTrue();
  }

  @Test
  void createOrder_whatsappNeedsAPhoneNumber() {
    CreateSalesOrderRequest request = baseRequest();
    request.setContactName("Ayşe Demir");
    request.setContactWhatsapp(true);
    stubSuccessfulCreate();

    SalesOrderDto created = salesOrderService.createOrder(request);

    assertThat(created.getContactName()).isEqualTo("Ayşe Demir");
    assertThat(created.isContactWhatsapp()).isFalse();
  }

  @Test
  void createOrder_aLineToleranceOutsideZeroToAHundredIsRejectedBeforeSaving() {
    TenantContext.setCurrentUserId(UUID.randomUUID());
    CreateSalesOrderRequest request = baseRequest();
    SalesOrderLineRequest line = lineRequest(new BigDecimal("500"), BigDecimal.TEN, "USD");
    line.setToleranceUpPct(new BigDecimal("101"));
    request.setLines(List.of(line));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("between 0 and 100");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createOrder_unpricedLineKeepsItsCurrencyAndIsCountedNotTotalled() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("10"), null, "USD"),
            lineRequest(new BigDecimal("2"), new BigDecimal("15"), "TRY")));
    stubSuccessfulCreate();
    echoSavedLines();

    SalesOrderDto created = salesOrderService.createOrder(request);

    assertThat(created.getUnpricedLineCount()).isEqualTo(1);
    assertThat(created.getTotals())
        .singleElement()
        .satisfies(
            total -> {
              assertThat(total.currency()).isEqualTo("TRY");
              assertThat(total.grandTotal()).isEqualByComparingTo("30");
            });
    assertThat(created.getLines().getFirst().getCurrency()).isEqualTo("USD");
    assertThat(created.getLines().getFirst().getUnitPrice()).isNull();
  }

  @Test
  void createOrder_withoutLinesHasNoTotals() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of());
    stubSuccessfulCreate();

    SalesOrderDto created = salesOrderService.createOrder(request);

    assertThat(created.getTotals()).isEmpty();
    assertThat(created.getUnpricedLineCount()).isZero();
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isNull();
  }

  @Test
  void createOrder_homogeneousLineModuleTypes_derivesHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setModuleType(ModuleType.YARN);
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", ModuleType.FABRIC)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository, times(1)).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void createOrder_mixedLineModuleTypes_derivesNullHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setModuleType(ModuleType.FIBER);
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", ModuleType.YARN)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isNull();
  }

  @Test
  void createOrder_nullLineModuleTypeIsIgnoredWhenDerivingHeaderModuleType() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY", ModuleType.FABRIC),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY", null)));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void createOrder_pricedLineWithoutCurrencyIsRejectedBeforeAnythingIsSaved() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of(lineRequest(BigDecimal.ONE, BigDecimal.TEN, null)));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("must name its currency");
    verify(lineRepository, never()).saveAll(any());
  }

  @Test
  void createOrder_lineDiscountGreaterThanTheLineAmountIsRejected() {
    CreateSalesOrderRequest request = baseRequest();
    SalesOrderLineRequest line = lineRequest(BigDecimal.ONE, BigDecimal.TEN, "TRY");
    line.setDiscountAmount(new BigDecimal("11.00"));
    request.setLines(List.of(line));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Line discount cannot exceed the line amount");
    verify(lineRepository, never()).saveAll(any());
  }

  @Test
  void createOrder_taxWithoutAnAgreedPriceIsRejected() {
    CreateSalesOrderRequest request = baseRequest();
    SalesOrderLineRequest line = lineRequest(BigDecimal.ONE, null, "TRY");
    line.setTaxAmount(BigDecimal.ONE);
    request.setLines(List.of(line));
    stubCreateUntilTotalCalculation();

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("need an agreed unit price");
  }

  @Test
  void createOrder_echoesThePersistedLinesInTheResponse() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(
        List.of(
            lineRequest(new BigDecimal("3"), new BigDecimal("10.00"), "TRY"),
            lineRequest(new BigDecimal("4"), new BigDecimal("2.50"), "TRY")));
    stubSuccessfulCreate();
    when(lineRepository.saveAll(ArgumentMatchers.<List<SalesOrderLine>>any()))
        .thenAnswer(
            invocation -> {
              List<SalesOrderLine> lines = invocation.getArgument(0);
              lines.forEach(line -> ReflectionTestUtils.setField(line, "id", UUID.randomUUID()));
              return lines;
            });

    SalesOrderDto created = salesOrderService.createOrder(request);

    // Regression guard: creation used to answer with SalesOrderDto.from(order, partner), an
    // overload that substituted an empty line list. Callers that chain off the response — the
    // demo seeder among them — silently skipped everything downstream of the lines.
    assertThat(created.getLines()).hasSize(2);
    assertThat(created.getLines()).allSatisfy(line -> assertThat(line.getId()).isNotNull());
  }

  @Test
  void createOrder_withoutLinesAnswersWithAnEmptyLineList() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of());
    stubSuccessfulCreate();

    SalesOrderDto created = salesOrderService.createOrder(request);

    assertThat(created.getLines()).isEmpty();
    verify(lineRepository, never()).saveAll(ArgumentMatchers.<List<SalesOrderLine>>any());
  }

  @Test
  void createOrder_validatesEveryCatalogueLineAgainstTheOrderCustomerBeforePersisting() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of(lineRequest(new BigDecimal("500"), new BigDecimal("4.20"), "TRY")));
    stubSuccessfulCreate();

    salesOrderService.createOrder(request);

    var ordered = org.mockito.Mockito.inOrder(orderIntakeHooks, orderRepository);
    ordered.verify(orderIntakeHooks).validateLines(tenantId, tradingPartnerId, request.getLines());
    ordered.verify(orderRepository).save(any(SalesOrder.class));
  }

  @Test
  void createOrder_persistsNothingWhenACatalogueRuleFails() {
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of(lineRequest(new BigDecimal("500"), new BigDecimal("4.20"), "TRY")));
    when(partnerResolver.resolvePartnerId(tenantId, requestPartnerId)).thenReturn(tradingPartnerId);
    org.mockito.Mockito.doThrow(
            com.fabricmanagement.sales.common.exception.OrderIntakeException.unitNotAllowed("KG"))
        .when(orderIntakeHooks)
        .validateLines(tenantId, tradingPartnerId, request.getLines());

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderIntakeException.class);
    verify(orderRepository, never()).save(any(SalesOrder.class));
  }

  @Test
  void createOrder_copiesDistributionFieldsToTheLine() {
    CreateSalesOrderRequest request = baseRequest();
    UUID colorId = UUID.randomUUID();
    SalesOrderLineRequest line = lineRequest(new BigDecimal("500"), new BigDecimal("4.20"), "TRY");
    line.setColorId(colorId);
    line.setFinishedWidth(new BigDecimal("160"));
    line.setFinishedWidthUnit("cm");
    line.setRequestedDeliveryDate(LocalDate.of(2026, 11, 12));
    line.setSingleLotRequired(true);
    request.setLines(List.of(line));
    stubSuccessfulCreate();
    when(lineRepository.saveAll(ArgumentMatchers.<List<SalesOrderLine>>any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    salesOrderService.createOrder(request);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SalesOrderLine>> lines = ArgumentCaptor.forClass(List.class);
    verify(lineRepository).saveAll(lines.capture());
    SalesOrderLine saved = lines.getValue().getFirst();
    assertThat(saved.getColorId()).isEqualTo(colorId);
    assertThat(saved.getFinishedWidth()).isEqualByComparingTo("160");
    assertThat(saved.getFinishedWidthUnit()).isEqualTo("CM");
    assertThat(saved.getRequestedDeliveryDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(saved.isSingleLotRequired()).isTrue();
  }

  @Test
  void createOrder_savesCustomRequestWithItsCatalogueLinesAndActor() {
    UUID actor = UUID.randomUUID();
    TenantContext.setCurrentUserId(actor);
    CreateSalesOrderRequest request = baseRequest();
    request.setLines(List.of(lineRequest(new BigDecimal("500"), BigDecimal.TEN, "TRY")));
    var customRequest = customRequest("Match the customer's sample");
    request.setCustomRequests(List.of(customRequest));
    stubSuccessfulCreate();
    when(lineRepository.saveAll(ArgumentMatchers.<List<SalesOrderLine>>any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SalesOrderDto result = salesOrderService.createOrder(request);

    var sequence =
        org.mockito.Mockito.inOrder(orderRepository, lineRepository, customerRequestService);
    sequence.verify(orderRepository).save(any(SalesOrder.class));
    sequence.verify(lineRepository).saveAll(any());
    sequence.verify(customerRequestService).create(result.getId(), customRequest, actor);
    assertThat(result.getLines()).hasSize(1);
  }

  @Test
  void createOrder_allowsCustomOnlyDraftWithoutAnUnboundCatalogueLine() {
    UUID actor = UUID.randomUUID();
    TenantContext.setCurrentUserId(actor);
    CreateSalesOrderRequest request = baseRequest();
    var customRequest = customRequest("Sample without quantity yet");
    request.setCustomRequests(List.of(customRequest));
    stubSuccessfulCreate();

    SalesOrderDto result = salesOrderService.createOrder(request);

    assertThat(result.getLines()).isEmpty();
    verify(lineRepository, never()).saveAll(any());
    verify(customerRequestService).create(result.getId(), customRequest, actor);
  }

  @Test
  void createOrder_propagatesCustomRequestFailureToTheEnclosingTransaction() {
    CreateSalesOrderRequest request = baseRequest();
    request.setCustomRequests(List.of(customRequest("")));
    stubCreateUntilTotalCalculation();
    when(orderRepository.save(any(SalesOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("Request evidence is required"))
        .when(customerRequestService)
        .create(any(), any(), any());

    assertThatThrownBy(() -> salesOrderService.createOrder(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Request evidence is required");
    verify(partnerService, never()).findById(any(), any());
  }

  private com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos.RequestInput customRequest(
      String description) {
    return new com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos.RequestInput(
        description, null, null, null, null, null, null, null, null, null, List.of());
  }

  private CreateSalesOrderRequest baseRequest() {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(requestPartnerId);
    request.setOrderType(OrderType.SALES);
    request.setOrderDate(LocalDate.of(2026, 6, 1));
    return request;
  }

  private SalesOrderLineRequest lineRequest(
      BigDecimal requestedQty, BigDecimal unitPrice, String currency) {
    return lineRequest(requestedQty, unitPrice, currency, null);
  }

  private SalesOrderLineRequest lineRequest(
      BigDecimal requestedQty, BigDecimal unitPrice, String currency, ModuleType moduleType) {
    return SalesOrderLineRequest.builder()
        .productId(UUID.randomUUID())
        .productDesc("Cotton fabric")
        .requestedQty(requestedQty)
        .unit("KG")
        .unitPrice(unitPrice)
        .currency(currency)
        .moduleType(moduleType)
        .build();
  }

  private void echoSavedLines() {
    when(lineRepository.saveAll(ArgumentMatchers.<List<SalesOrderLine>>any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private void stubSuccessfulCreate() {
    stubCreateUntilTotalCalculation();
    when(orderRepository.save(any(SalesOrder.class)))
        .thenAnswer(
            invocation -> {
              SalesOrder order = invocation.getArgument(0);
              ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
              return order;
            });
    when(partnerService.findById(tenantId, tradingPartnerId)).thenReturn(Optional.empty());
  }

  private void stubCreateUntilTotalCalculation() {
    when(partnerResolver.resolvePartnerId(tenantId, requestPartnerId)).thenReturn(tradingPartnerId);
    when(documentNumberGenerator.generate(
            tenantId, "SALES_ORDER", "SO", LocalDate.of(2026, 6, 1), 5))
        .thenReturn("SO-20260601-00001");
  }
}
