package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.app.DraftStockSelectionService;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionRequests;
import com.fabricmanagement.sales.quote.domain.*;
import com.fabricmanagement.sales.quote.dto.*;
import com.fabricmanagement.sales.quote.infra.repository.QuoteStockOperationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class QuoteRollSelectionServiceTest {
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      id = UUID.randomUUID(),
      lineId = UUID.randomUUID();
  final QuoteStockTargetAccess access = mock(QuoteStockTargetAccess.class);
  final DraftStockSelectionService stock = mock(DraftStockSelectionService.class);
  final QuoteService quotes = mock(QuoteService.class);
  final QuoteStockOperationRepository operations = mock(QuoteStockOperationRepository.class);
  final StockUnitSoftHoldPort holds = mock(StockUnitSoftHoldPort.class);
  final EntityManager entityManager = mock(EntityManager.class);
  final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  final QuoteRollSelectionService service =
      new QuoteRollSelectionService(
          access,
          stock,
          quotes,
          operations,
          holds,
          entityManager,
          mapper,
          Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC),
          mock(com.fabricmanagement.sales.orderintake.app.ProvisionalStockLifecycleService.class));
  Quote quote;
  QuoteLine line;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    var query = mock(jakarta.persistence.Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    quote = new Quote();
    quote.setId(id);
    quote.setVersion(4L);
    line = new QuoteLine();
    line.setId(lineId);
    line.setRequestedQty(new BigDecimal("2050"));
    line.captureCustomerRequest(new BigDecimal("2050"));
    quote.addLine(line);
    when(access.lock(id, actor)).thenReturn(quote);
    when(access.writable(id, actor)).thenReturn(quote);
    when(access.line(quote, lineId)).thenReturn(line);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void staleAggregateCannotTransferOrReleaseAnyRolls() {
    var proof =
        new DraftStockSelectionRequests.Proof(
            UUID.randomUUID(), 0L, List.of(UUID.randomUUID()), true);
    assertThatThrownBy(
            () ->
                service.apply(
                    id,
                    lineId,
                    new QuoteRollSelectionRequests.Apply(UUID.randomUUID(), 3L, proof),
                    actor))
        .isInstanceOf(OrderIntakeException.class)
        .hasMessageContaining("quote changed");
    verifyNoInteractions(stock, holds, quotes);
    verify(operations, never()).saveAndFlush(any());
  }

  @Test
  void policyAndItsReceiptReplayWithoutAnotherMutation() throws Exception {
    var request =
        new QuoteRollSelectionRequests.Policy(
            UUID.randomUUID(),
            4L,
            SalesQuantityPolicy.EXACT,
            "Customer requires the exact quantity");
    when(holds.heldPieceIds(lineId)).thenReturn(List.of());
    var result = service.policy(id, lineId, request, actor);
    var receipt = org.mockito.ArgumentCaptor.forClass(QuoteStockOperation.class);
    verify(operations).saveAndFlush(receipt.capture());
    // The JSONB receipt has a real deserialisable record, not an entity or private constructor DTO.
    var restored = mapper.readValue(mapper.writeValueAsString(result), QuoteRollSelectionDto.class);
    assertThat(restored).isEqualTo(result);
    when(operations.findByTenantIdAndOperationId(tenant, request.operationId()))
        .thenReturn(Optional.of(receipt.getValue()));
    quote.setVersion(99L);
    assertThat(service.policy(id, lineId, request, actor)).isEqualTo(result);
    verify(quotes, times(1)).advanceStockRevision(quote, 4L);
    verify(operations, times(1)).saveAndFlush(any());
    assertThat(result.quantityTerms().policy()).isEqualTo(SalesQuantityPolicy.EXACT);
    assertThat(result.customerRequestQuantity()).isEqualByComparingTo("2050");
  }

  @Test
  void anOperationCannotBeReusedForAnotherPayloadOrActor() {
    UUID operation = UUID.randomUUID();
    var recorded =
        QuoteStockOperation.record(
            id,
            actor,
            operation,
            "another fingerprint",
            new QuoteRollSelectionDto(
                id,
                lineId,
                4,
                new BigDecimal("2050"),
                null,
                new SalesQuantityTermsDto(SalesQuantityPolicy.UNRECORDED, null, null, null),
                List.of()));
    when(operations.findByTenantIdAndOperationId(tenant, operation))
        .thenReturn(Optional.of(recorded));
    assertThatThrownBy(
            () ->
                service.remove(
                    id, lineId, new QuoteRollSelectionRequests.Remove(operation, 4L), actor))
        .isInstanceOf(OrderIntakeException.class)
        .hasMessageContaining("another quote command");
    verifyNoInteractions(stock, holds, quotes);
  }

  @Test
  void untrustedProofCannotCreateACommercialLineWithoutFormIdentity() {
    var lineRequest = new AddQuoteLineRequest();
    assertThatThrownBy(
            () ->
                service.create(
                    id,
                    new QuoteRollSelectionRequests.Create(UUID.randomUUID(), 4L, lineRequest),
                    actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(stock, quotes, holds);
  }
}
