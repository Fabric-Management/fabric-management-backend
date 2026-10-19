package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseProperties;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.app.LiveEditSessionService;
import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelectionOperation;
import com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionDto;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionRequests;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionOperationRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import com.fabricmanagement.sales.salesorder.app.CatalogLineValidator;
import com.fabricmanagement.sales.salesorder.app.SalesOrderLeaseGuard;
import com.fabricmanagement.sales.salesorder.app.SalesOrderRevision;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provisional stock uses common leases; applying a commercial quantity is a separate action. */
@Service
@RequiredArgsConstructor
public class DraftStockSelectionService {
  private final DraftStockSelectionRepository selections;
  private final DraftStockSelectionOperationRepository operations;
  private final LiveEditSessionService sessions;
  private final LiveEditLeaseService leases;
  private final LiveEditLeaseProperties policy;
  private final StockUnitOwnershipPort ownership;
  private final StockUnitSoftHoldPort holds;
  private final QuantityEvaluationService evaluation;
  private final ProductSalesDefinitionQueryService products;
  private final CatalogLineValidator catalogue;
  private final SalesAccessScopeResolver permissions;
  private final com.fabricmanagement.platform.user.app.UserQueryService users;
  private final OrderIntakeAccess access;
  private final SalesOrderRevision revision;
  private final SalesOrderLeaseGuard editLeases;
  private final LineAdjustmentGuard adjustments;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Transactional
  public DraftStockSelectionDto open(DraftStockSelectionRequests.Open request, UUID actor) {
    requireWrite(actor);
    var product =
        products
            .find(TenantContext.requireTenantId(), request.productId())
            .filter(value -> value.active() && value.productType() == ProductType.FABRIC)
            .orElseThrow(() -> OrderIntakeException.productNotAvailable(request.productId()));
    if ((request.targetOrderId() == null) != (request.targetLineId() == null))
      throw OrderIntakeException.rule(
          "STOCK_TARGET_INCOMPLETE", "Name both the saved order and its line");
    if (request.targetOrderId() != null) {
      var target = access.writableOrderFresh(request.targetOrderId(), actor);
      var line = access.line(target, request.targetLineId());
      if (!line.getProductId().equals(request.productId()))
        throw OrderIntakeException.productNotAvailable(request.productId());
    }
    ownership.lockProduct(product.productId());
    var previous =
        selections.findByTenantIdAndFormInstanceIdAndClientLineIdAndActorIdAndStateIn(
            TenantContext.requireTenantId(),
            request.formInstanceId(),
            request.clientLineId(),
            actor,
            List.of(DraftStockSelection.State.OPEN, DraftStockSelection.State.HELD));
    if (previous.isPresent()) {
      if (!previous.get().getProductId().equals(request.productId()))
        throw OrderIntakeException.conflict(
                "STOCK_PRODUCT_CHANGED",
                "Close the form line's previous stock session before changing its product")
            .withDetail("clientLineId", request.clientLineId())
            .withDetail("selectionId", previous.get().getId());
      if (!Objects.equals(previous.get().getTargetOrderId(), request.targetOrderId())
          || !Objects.equals(previous.get().getTargetLineId(), request.targetLineId()))
        throw OrderIntakeException.conflict(
            "STOCK_TARGET_CHANGED", "This form session belongs to another saved line");
      return dto(previous.get());
    }
    var session = sessions.open(StockUnitLeaseKeys.resource(request.productId()), actor);
    var row =
        DraftStockSelection.open(
            request.formInstanceId(),
            request.clientLineId(),
            request.productId(),
            actor,
            session.getId());
    row.bindTarget(request.targetOrderId(), request.targetLineId());
    return dto(selections.saveAndFlush(row));
  }

  @Transactional
  public DraftStockSelectionDto status(UUID selectionId, UUID actor) {
    requireWrite(actor);
    return dto(load(selectionId, actor));
  }

  @Transactional
  public DraftStockSelectionDto acquire(
      UUID selectionId, DraftStockSelectionRequests.Acquire request, UUID actor) {
    requireWrite(actor);
    var row = load(selectionId, actor);
    String hash = fingerprint(request);
    var replay = replay(request.operationId(), row, actor, hash);
    if (replay != null) return replay;
    requireMutable(row);
    requireRevision(row, request.expectedRevision());
    var activeFormLine =
        selections.findByTenantIdAndFormInstanceIdAndClientLineIdAndActorIdAndStateIn(
            TenantContext.requireTenantId(),
            row.getFormInstanceId(),
            row.getClientLineId(),
            actor,
            List.of(DraftStockSelection.State.OPEN, DraftStockSelection.State.HELD));
    if (activeFormLine.isPresent() && !activeFormLine.get().getId().equals(row.getId()))
      throw stale(row);
    boolean periodStillHeld =
        leases.held(StockUnitLeaseKeys.resource(row.getProductId()), 1).stream()
            .anyMatch(
                lease ->
                    lease.getSessionId().equals(row.getSessionId())
                        && row.getTokens().contains(lease.getToken()));
    if (periodStillHeld || request.previousTokens() == null || !request.previousTokens().isEmpty())
      requireTokens(row, request.previousTokens());
    if (!row.getProductId().equals(request.line().productId())) throw stale(row);
    validateCatalogue(request.line());
    requireTargetEditable(row, actor);
    var resource = StockUnitLeaseKeys.resource(row.getProductId());
    if (sessions.live(resource).stream()
        .noneMatch(session -> session.getId().equals(row.getSessionId())))
      row.reopenSession(sessions.open(resource, actor).getId());
    else sessions.renew(resource, row.getSessionId(), actor);
    var run = evaluation.run(input(row, request.line()));
    QuantityOption option =
        run.evaluation().result().options().stream()
            .filter(value -> value.optionKey().equals(request.optionKey()))
            .findFirst()
            .orElseThrow(() -> stale(row));
    if (!option.applicable())
      throw OrderIntakeException.conflict(
          "OPTION_NOT_APPLICABLE", "Selected stock is not free for this draft line");
    ownership.assertAvailable(option.pieceIds(), row.getTargetLineId(), row.getSessionId());
    var granted =
        leases.replace(
            StockUnitLeaseKeys.resource(row.getProductId()),
            1,
            row.getSessionId(),
            actor,
            option.pieceIds().stream().map(StockUnitLeaseKeys::key).toList());
    if (!(granted instanceof LiveEditLeaseService.Granted owned))
      throw OrderIntakeException.conflict(
          "STOCK_JUST_TAKEN", "Selected rolls are being prepared in another draft");
    row.hold(
        request.line(),
        option,
        owned.leases().stream().map(LiveEditLease::getToken).toList(),
        owned.leases().stream()
            .map(LiveEditLease::getExpiresAt)
            .min(java.util.Comparator.naturalOrder())
            .orElseThrow());
    // Session renewal clears the persistence context; use the merged instance and its new version.
    DraftStockSelection saved = selections.saveAndFlush(row);
    DraftStockSelectionDto result = dto(saved);
    operations.save(
        DraftStockSelectionOperation.record(
            request.operationId(), row.getId(), actor, hash, result));
    return result;
  }

  @Transactional
  public DraftStockSelectionDto renew(
      UUID selectionId, DraftStockSelectionRequests.Tokens request, UUID actor) {
    requireWrite(actor);
    var row = load(selectionId, actor);
    requireMutable(row);
    requireRevision(row, request.expectedRevision());
    requireTokens(row, request.tokens());
    requireTargetEditable(row, actor);
    verify(row, request.tokens());
    sessions.renew(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
    var answer =
        leases.renew(
            StockUnitLeaseKeys.resource(row.getProductId()),
            1,
            row.getSessionId(),
            actor,
            request.tokens());
    if (!answer.lost().isEmpty()) throw stale(row);
    // Renewal extends common lease periods, not the commercial selection revision. An applied
    // choice must not become stale every thirty seconds merely because its ownership was renewed.
    return dto(row);
  }

  @Transactional
  public void release(UUID selectionId, DraftStockSelectionRequests.Tokens request, UUID actor) {
    requireWrite(actor);
    var row = load(selectionId, actor);
    // A delayed pre-save release cannot touch the order's new ownership period.
    if (row.getState() != DraftStockSelection.State.HELD) return;
    if (!Objects.equals(row.getVersion(), request.expectedRevision())
        || !Set.copyOf(row.getTokens()).equals(Set.copyOf(request.tokens()))) return;
    leases.release(
        StockUnitLeaseKeys.resource(row.getProductId()),
        row.getSessionId(),
        actor,
        request.tokens());
    row.release();
    selections.save(row);
  }

  @Transactional
  public void close(UUID selectionId, DraftStockSelectionRequests.Close request, UUID actor) {
    requireWrite(actor);
    var row = load(selectionId, actor);
    if (row.getState() != DraftStockSelection.State.OPEN
        && row.getState() != DraftStockSelection.State.HELD
        && row.getState() != DraftStockSelection.State.RELEASED) return;
    if (!Objects.equals(row.getVersion(), request.expectedRevision())
        || request.tokens().size() != Set.copyOf(request.tokens()).size()
        || !Set.copyOf(row.getTokens()).equals(Set.copyOf(request.tokens()))) return;
    sessions.close(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
    row.release();
    selections.saveAndFlush(row);
  }

  /** A create echoes the commercial quantity already shown by Apply; do not silently replace it. */
  @Transactional
  public void transferOnCreate(
      SalesOrder order,
      SalesOrderLine line,
      DraftStockSelectionRequests.Proof proof,
      UUID clientLineId,
      UUID actor) {
    requireWrite(actor);
    var selected = load(proof.selectionId(), actor);
    if (selected.getOption() == null
        || line.getRequestedQty().compareTo(selected.getOption().quantity()) != 0)
      throw stale(selected);
    transfer(order, line, proof, clientLineId, actor);
  }

  /**
   * Joins the order creation transaction; every line must transfer or the entire order rolls back.
   */
  @Transactional
  public void transfer(
      SalesOrder order,
      SalesOrderLine line,
      DraftStockSelectionRequests.Proof proof,
      UUID clientLineId,
      UUID actor) {
    requireWrite(actor);
    var row = load(proof.selectionId(), actor);
    requireMutable(row);
    requireRevision(row, proof.selectionRevision());
    requireTokens(row, proof.tokens());
    if (!row.getClientLineId().equals(clientLineId) || !sameLine(row, order, line))
      throw stale(row);
    verify(row, proof.tokens());
    QuantityOption option = currentOption(row);
    if ((option.outsideAgreedTolerance() || option.needsRemnantAcknowledgement())
        && !proof.confirmed()) {
      throw OrderIntakeException.rule(
          "CONFIRMATION_REQUIRED",
          "Acknowledge the proposal's quantity difference or remainder before applying");
    }
    ownership.assertAvailable(option.pieceIds(), line.getId(), row.getSessionId());
    line.applyStockQuantity(row.getRequest().requestedQty(), option.quantity());
    adjustments.assertFits(line);
    holds.replaceOrderHolds(
        order.getId(),
        order.getOrderNumber(),
        line.getId(),
        actor,
        row.getSessionId(),
        option.pieceIds(),
        option.lots().stream()
            .map(
                lot ->
                    new StockUnitSoftHoldPort.OrderLot(
                        lot.batchId(), lot.quantity(), line.getUnit()))
            .toList());
    // The permanent hold and ending this period commit together: no availability gap.
    leases.release(
        StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor, proof.tokens());
    row.transfer(order.getId(), line.getId(), StockSelectionTerms.fingerprint(order, line));
    row.orderVersion(order.getVersion());
    selections.saveAndFlush(row);
  }

  @Transactional
  public DraftStockSelectionDto apply(
      UUID orderId, UUID lineId, DraftStockSelectionRequests.Apply request, UUID actor) {
    requireWrite(actor);
    SalesOrder order = access.writableOrderFresh(orderId, actor);
    revision.lockFresh(order);
    access.requireActive(order);
    var row = load(request.selection().selectionId(), actor);
    String hash = fingerprint(List.of(orderId, lineId, request));
    var repeated = replay(request.operationId(), row, actor, hash);
    if (repeated != null) return repeated;
    if (!Objects.equals(order.getVersion(), request.expectedVersion()))
      throw OrderIntakeException.conflict(
          "STALE_VERSION", "The order changed; reload before applying");
    if (order.getStatus() != OrderStatus.DRAFT)
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Stock is applied while the order is a draft");
    order.assertCommercialContentEditable();
    editLeases.assertLineFieldFree(order, lineId, OrderEditKey.LINE_QUANTITY, actor);
    var line = access.line(order, lineId);
    revision.lockFresh(line);
    supersede(lineId);
    transfer(order, line, request.selection(), row.getClientLineId(), actor);
    revision.linesChanged(order);
    row.orderVersion(order.getVersion());
    selections.saveAndFlush(row);
    var result = dto(row);
    operations.save(
        DraftStockSelectionOperation.record(
            request.operationId(), row.getId(), actor, hash, result));
    return result;
  }

  @Transactional
  public DraftStockSelectionDto remove(
      UUID orderId, UUID lineId, DraftStockSelectionRequests.Remove request, UUID actor) {
    requireWrite(actor);
    var order = access.writableOrderFresh(orderId, actor);
    revision.lockFresh(order);
    access.requireActive(order);
    var line = access.line(order, lineId);
    ownership.lockProduct(line.getProductId());
    String hash = fingerprint(List.of("REMOVE", orderId, lineId, request));
    var recorded =
        operations.findByTenantIdAndOperationId(
            TenantContext.requireTenantId(), request.operationId());
    if (recorded.isPresent()) {
      var old = recorded.get();
      if (!old.getActorId().equals(actor) || !old.getRequestHash().equals(hash))
        throw OrderIntakeException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "The operation belongs to another command");
      return old.getResult();
    }
    if (!Objects.equals(order.getVersion(), request.expectedVersion())
        || order.getStatus() != OrderStatus.DRAFT)
      throw OrderIntakeException.conflict(
          "STALE_VERSION", "The draft changed; reload before removing its stock choice");
    order.assertCommercialContentEditable();
    editLeases.assertLineFieldFree(order, lineId, OrderEditKey.LINE_QUANTITY, actor);
    revision.lockFresh(line);
    var selected =
        selections
            .findByTenantIdAndSalesOrderLineIdInAndState(
                TenantContext.requireTenantId(),
                List.of(lineId),
                DraftStockSelection.State.TRANSFERRED)
            .stream()
            .findFirst()
            .orElseThrow(() -> OrderIntakeException.notFound("Applied stock choice", lineId));
    line.restoreCustomerRequestQuantity();
    adjustments.assertFits(line);
    holds.releaseHolds(lineId);
    selected.release();
    revision.linesChanged(order);
    selected.orderVersion(order.getVersion());
    selections.saveAndFlush(selected);
    var result = dto(selected);
    operations.save(
        DraftStockSelectionOperation.record(
            request.operationId(), selected.getId(), actor, hash, result));
    return result;
  }

  @Transactional
  public void discardUnapplied(
      DraftStockSelectionRequests.Proof proof, UUID clientLineId, UUID actor) {
    requireWrite(actor);
    var row = load(proof.selectionId(), actor);
    requireTokens(row, proof.tokens());
    requireRevision(row, proof.selectionRevision());
    if (!row.getClientLineId().equals(clientLineId)) throw stale(row);
    if (row.getState() == DraftStockSelection.State.OPEN
        || row.getState() == DraftStockSelection.State.HELD) {
      sessions.close(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
      row.release();
      selections.save(row);
    }
  }

  private QuantityOption currentOption(DraftStockSelection row) {
    var request = row.getRequest();
    var selected =
        new OrderIntakeStockPreviewRequest(
            request.productId(),
            request.colorId(),
            request.finishedWidth(),
            request.finishedWidthUnit(),
            request.customerId(),
            request.requestedQty(),
            request.unit(),
            request.singleLotRequired(),
            request.toleranceUpPct(),
            request.toleranceDownPct(),
            row.getOption().pieceIds());
    var result = evaluation.run(input(row, selected)).evaluation().result();
    return result.options().stream()
        .filter(
            option -> Set.copyOf(option.pieceIds()).equals(Set.copyOf(row.getOption().pieceIds())))
        .filter(QuantityOption::applicable)
        .filter(option -> option.quantity().compareTo(row.getOption().quantity()) == 0)
        .filter(option -> option.toleranceStatus() == row.getOption().toleranceStatus())
        .filter(option -> option.leavesRemnant() == row.getOption().leavesRemnant())
        .findFirst()
        .orElseThrow(() -> stale(row));
  }

  private QuantityEvaluationService.EvaluationRequest input(
      DraftStockSelection row, OrderIntakeStockPreviewRequest request) {
    return new QuantityEvaluationService.EvaluationRequest(
        row.getTargetLineId(),
        request.productId(),
        request.colorId(),
        request.finishedWidth(),
        request.finishedWidthUnit(),
        request.customerId(),
        request.requestedQty(),
        request.unit(),
        Boolean.TRUE.equals(request.singleLotRequired()),
        request.toleranceUpPct(),
        request.toleranceDownPct(),
        List.of(),
        request.manualPieceIds(),
        row.getSessionId());
  }

  private void validateCatalogue(OrderIntakeStockPreviewRequest request) {
    SalesOrderLineRequest input =
        SalesOrderLineRequest.builder()
            .productId(request.productId())
            .colorId(request.colorId())
            .finishedWidth(request.finishedWidth())
            .finishedWidthUnit(request.finishedWidthUnit())
            .unit(request.unit())
            .build();
    catalogue.validate(TenantContext.requireTenantId(), request.customerId(), List.of(input));
  }

  private boolean sameLine(DraftStockSelection row, SalesOrder order, SalesOrderLine line) {
    var request = row.getRequest();
    if (row.getTargetOrderId() != null
        && (!row.getTargetOrderId().equals(order.getId())
            || !row.getTargetLineId().equals(line.getId()))) return false;
    return request != null
        && row.getProductId().equals(line.getProductId())
        && Objects.equals(request.colorId(), line.getColorId())
        && equalDecimal(request.finishedWidth(), line.getFinishedWidth())
        && Objects.equals(upper(request.finishedWidthUnit()), upper(line.getFinishedWidthUnit()))
        && Objects.equals(request.customerId(), order.getTradingPartnerId())
        && Objects.equals(upper(request.unit()), upper(line.getUnit()))
        && equalDecimal(request.requestedQty(), line.getCustomerRequestQty())
        && equalDecimal(request.toleranceUpPct(), line.getToleranceUpPct())
        && equalDecimal(request.toleranceDownPct(), line.getToleranceDownPct())
        && Boolean.TRUE.equals(request.singleLotRequired()) == line.isSingleLotRequired();
  }

  private static String upper(String value) {
    return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private static boolean equalDecimal(BigDecimal left, BigDecimal right) {
    return left == null ? right == null : right != null && left.compareTo(right) == 0;
  }

  private void verify(DraftStockSelection row, List<UUID> tokens) {
    if (row.getOption() == null || row.getState() != DraftStockSelection.State.HELD)
      throw stale(row);
    var proof =
        leases.verify(
            StockUnitLeaseKeys.resource(row.getProductId()),
            1,
            row.getSessionId(),
            Set.copyOf(tokens),
            row.getActorId(),
            row.getOption().pieceIds().stream()
                .map(StockUnitLeaseKeys::key)
                .collect(Collectors.toSet()));
    if (!proof.proven()) throw stale(row);
  }

  private DraftStockSelection load(UUID id, UUID actor) {
    var row =
        selections
            .findByTenantIdAndId(TenantContext.requireTenantId(), id)
            .filter(value -> value.getActorId().equals(actor))
            .orElseThrow(() -> OrderIntakeException.notFound("Draft stock selection", id));
    ownership.lockProduct(row.getProductId());
    entityManager.refresh(row);
    return row;
  }

  private void requireTargetEditable(DraftStockSelection row, UUID actor) {
    if (row.getTargetOrderId() == null) return;
    var order = access.writableOrderFresh(row.getTargetOrderId(), actor);
    access.line(order, row.getTargetLineId());
    if (order.getStatus() != OrderStatus.DRAFT) throw stale(row);
    order.assertCommercialContentEditable();
  }

  private void requireWrite(UUID actor) {
    if (!users.isActive(TenantContext.requireTenantId(), actor)
        || permissions
                .resolve(
                    TenantContext.requireTenantId(),
                    actor,
                    "write",
                    SalesAccessScopeResolver.PermissionFreshness.FRESH)
                .scope()
            == null)
      throw new AccessDeniedException("Sales write permission is required to hold stock");
  }

  private void requireMutable(DraftStockSelection row) {
    if (row.getSalesOrderId() != null
        || (row.getState() != DraftStockSelection.State.OPEN
            && row.getState() != DraftStockSelection.State.HELD
            && row.getState() != DraftStockSelection.State.RELEASED)) throw stale(row);
  }

  private void requireRevision(DraftStockSelection row, Long version) {
    if (!Objects.equals(row.getVersion(), version)) throw stale(row);
  }

  private void requireTokens(DraftStockSelection row, List<UUID> tokens) {
    if (tokens == null
        || tokens.size() != Set.copyOf(tokens).size()
        || !Set.copyOf(row.getTokens()).equals(Set.copyOf(tokens))) throw stale(row);
  }

  private OrderIntakeException stale(DraftStockSelection row) {
    return OrderIntakeException.conflict(
            "STOCK_PROPOSAL_STALE", "Stock selection changed or expired; refresh the affected line")
        .withDetail("clientLineId", row.getClientLineId())
        .withDetail("selectionId", row.getId());
  }

  private DraftStockSelectionDto replay(
      UUID operation, DraftStockSelection row, UUID actor, String hash) {
    var previous =
        operations.findByTenantIdAndOperationId(TenantContext.requireTenantId(), operation);
    if (previous.isEmpty()) return null;
    var found = previous.get();
    if (!found.getActorId().equals(actor)
        || !found.getSelectionId().equals(row.getId())
        || !found.getRequestHash().equals(hash))
      throw OrderIntakeException.conflict(
          "IDEMPOTENCY_KEY_REUSED", "This operation already names another selection or payload");
    var recorded = found.getResult();
    if (Objects.equals(
        recorded.option(),
        row.getOption() == null
            ? null
            : com.fabricmanagement.sales.orderintake.dto.QuantityProposalDto.Option.from(
                row.getOption(), Set.of()))) return dto(row);
    return new DraftStockSelectionDto(
        recorded.selectionId(),
        recorded.sessionId(),
        recorded.clientLineId(),
        recorded.selectionRevision(),
        DraftStockSelectionDto.State.SUPERSEDED,
        recorded.policy(),
        recorded.expiresAt(),
        0,
        recorded.option(),
        List.of(),
        List.of(),
        recorded.salesOrderId(),
        recorded.salesOrderLineId(),
        recorded.orderVersion());
  }

  private String fingerprint(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Cannot fingerprint stock operation", ex);
    }
  }

  DraftStockSelectionDto dto(DraftStockSelection row) {
    var live =
        leases.held(StockUnitLeaseKeys.resource(row.getProductId()), 1).stream()
            .filter(
                lease ->
                    lease.getSessionId().equals(row.getSessionId())
                        && row.getTokens().contains(lease.getToken()))
            .toList();
    boolean held =
        row.getState() == DraftStockSelection.State.HELD
            && live.size() == row.getTokens().size()
            && !live.isEmpty();
    var state =
        row.getState() == DraftStockSelection.State.HELD && !held
            ? DraftStockSelectionDto.State.EXPIRED
            : DraftStockSelectionDto.State.valueOf(row.getState().name());
    var expiry =
        held
            ? live.stream()
                .map(LiveEditLease::getExpiresAt)
                .min(java.util.Comparator.naturalOrder())
                .orElseThrow()
            : null;
    long remaining = held ? Math.max(0, Duration.between(clock.instant(), expiry).toMillis()) : 0;
    boolean canAcquire =
        row.getSalesOrderId() == null
            && (state == DraftStockSelectionDto.State.OPEN
                || held
                || state == DraftStockSelectionDto.State.RELEASED
                || state == DraftStockSelectionDto.State.EXPIRED);
    var capabilities =
        List.of(
            new DraftStockSelectionDto.Capability(
                DraftStockSelectionDto.Action.ACQUIRE,
                canAcquire,
                canAcquire ? null : DraftStockSelectionDto.Reason.SELECTION_ENDED,
                null,
                false,
                false),
            new DraftStockSelectionDto.Capability(
                DraftStockSelectionDto.Action.APPLY,
                held,
                held ? null : DraftStockSelectionDto.Reason.OWNERSHIP_REQUIRED,
                null,
                false,
                row.getOption() != null
                    && (row.getOption().outsideAgreedTolerance()
                        || row.getOption().needsRemnantAcknowledgement())),
            new DraftStockSelectionDto.Capability(
                DraftStockSelectionDto.Action.RENEW,
                held,
                held ? null : DraftStockSelectionDto.Reason.SELECTION_EXPIRED,
                null,
                false,
                false),
            new DraftStockSelectionDto.Capability(
                DraftStockSelectionDto.Action.RELEASE,
                held,
                held ? null : DraftStockSelectionDto.Reason.SELECTION_ENDED,
                null,
                false,
                false),
            new DraftStockSelectionDto.Capability(
                DraftStockSelectionDto.Action.CLOSE,
                canAcquire,
                canAcquire ? null : DraftStockSelectionDto.Reason.SELECTION_ENDED,
                null,
                false,
                false));
    return new DraftStockSelectionDto(
        row.getId(),
        row.getSessionId(),
        row.getClientLineId(),
        row.getVersion() == null ? 0 : row.getVersion(),
        state,
        new DraftStockSelectionDto.Policy(
            policy.ttlSeconds(),
            policy.renewAfterSeconds(),
            policy.idleAfterSeconds(),
            policy.idleWarningSeconds(),
            policy.getMaxKeysPerRequest()),
        expiry,
        remaining,
        row.getOption() == null
            ? null
            : com.fabricmanagement.sales.orderintake.dto.QuantityProposalDto.Option.from(
                row.getOption(), Set.of()),
        held
            ? live.stream()
                .map(
                    lease ->
                        new DraftStockSelectionDto.Grant(
                            StockUnitLeaseKeys.pieceId(lease.key()),
                            lease.getToken(),
                            lease.getExpiresAt()))
                .toList()
            : List.of(),
        capabilities,
        row.getSalesOrderId(),
        row.getSalesOrderLineId(),
        row.getOrderVersion());
  }

  private void supersede(UUID lineId) {
    for (var previous :
        selections.findByTenantIdAndSalesOrderLineIdInAndState(
            TenantContext.requireTenantId(),
            List.of(lineId),
            DraftStockSelection.State.TRANSFERRED)) previous.supersede();
    selections.flush();
  }
}
