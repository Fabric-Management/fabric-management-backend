package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.Source;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.SourceKnowledge;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Builds and reads order-cover evidence behind grant and subject-scope checks. */
@Service
@RequiredArgsConstructor
public class OrderCoverEvidenceService {
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final OrderCoverEvidenceStreamRepository streams;
  private final OrderCoverEvidenceRepository evidence;
  private final OrderCoverEvidencePort production;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;
  private final OrderCoverObjectAccess objectAccess;

  @PreAuthorize(
      "@auth.can(authentication,'flowboard','read') and @auth.can(authentication,'sales','read')")
  public OrderCoverEvidenceDto refresh(UUID orderId, UUID caseId) {
    objectAccess.readable(orderId, requireActor());
    return appendWithRetry(orderId, caseId, false);
  }

  @PreAuthorize(
      "@auth.can(authentication,'flowboard','write') and @auth.can(authentication,'sales','write')")
  public OrderCoverEvidenceDto rebuild(UUID orderId, UUID caseId) {
    objectAccess.assertWritable(orderId, requireActor());
    return appendWithRetry(orderId, caseId, true);
  }

  private OrderCoverEvidenceDto appendWithRetry(UUID orderId, UUID caseId, boolean force) {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.setTimeout(30);
    for (int attempt = 1; ; attempt++) {
      try {
        return transaction.execute(status -> append(orderId, caseId, force));
      } catch (RuntimeException failure) {
        if (attempt >= 3 || !serializationConflict(failure)) throw failure;
      }
    }
  }

  private static boolean serializationConflict(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "40001".equals(sql.getSQLState())) return true;
    }
    return false;
  }

  private OrderCoverEvidenceDto append(UUID orderId, UUID caseId, boolean force) {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(caseId);
    UUID tenantId = TenantContext.requireTenantId();
    var stream = streams.lockScope(tenantId, caseId).orElse(null);
    if (force && stream == null) throw new OrderDomainException("Evidence scope not found", 404);
    if (stream == null) {
      var identity = OrderCoverEvidenceStream.create(tenantId, orderId, caseId);
      streams.establishScope(
          UUID.randomUUID(),
          tenantId,
          orderId,
          caseId,
          identity.getUid(),
          TenantContext.getCurrentUserId());
      stream =
          streams
              .lockScope(tenantId, caseId)
              .orElseThrow(() -> new OrderDomainException("Sales order not found", 404));
    }
    stream.requireOrder(orderId);
    Requirements requirements = requirements(tenantId, orderId, caseId);
    Inputs inputs = production.inspect(requirements);
    String fingerprint = fingerprint(requirements, inputs);
    var latest = evidence.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(tenantId, caseId);
    if (!force && latest.isPresent() && latest.get().getInputFingerprint().equals(fingerprint))
      return latest.get().toDto();
    var snapshot =
        OrderCoverEvidence.create(
            requirements,
            inputs,
            stream.nextRevision(),
            clock.instant(),
            fingerprint,
            OrderCoverEvidenceEvaluator.RULE_VERSION,
            OrderCoverEvidenceEvaluator.evaluate(requirements, inputs));
    streams.save(stream);
    return evidence.saveAndFlush(snapshot).toDto();
  }

  @Transactional(readOnly = true)
  @PreAuthorize(
      "@auth.can(authentication,'flowboard','read') and @auth.can(authentication,'sales','read')")
  public OrderCoverEvidenceDto read(UUID orderId, UUID evidenceId) {
    objectAccess.readable(orderId, requireActor());
    return load(orderId, evidenceId).toDto();
  }

  private static UUID requireActor() {
    return Objects.requireNonNull(
        TenantContext.getCurrentUserId(), "Authenticated actor is required");
  }

  /**
   * Caller locks task/order/case/lines first. Existing production rows stay locked until its
   * commit. A matching fingerprint is necessary, but does not prove atomic stock commitment (5b-3).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Revalidation revalidate(UUID orderId, UUID evidenceId) {
    var previous = load(orderId, evidenceId);
    var requirements = requirements(TenantContext.requireTenantId(), orderId, previous.getCaseId());
    var current = production.lockAndInspect(requirements);
    String fingerprint = fingerprint(requirements, current);
    return new Revalidation(previous.getInputFingerprint().equals(fingerprint), fingerprint);
  }

  public record Revalidation(boolean matches, String inputFingerprint) {}

  private OrderCoverEvidence load(UUID orderId, UUID evidenceId) {
    return evidence
        .findByTenantIdAndSalesOrderIdAndId(TenantContext.requireTenantId(), orderId, evidenceId)
        .orElseThrow(() -> new OrderDomainException("Order-cover evidence not found", 404));
  }

  private Requirements requirements(UUID tenantId, UUID orderId, UUID caseId) {
    var order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .orElseThrow(() -> new OrderDomainException("Sales order not found", 404));
    // Readability of archived evidence is independent of permission to recompute current evidence.
    if (!Boolean.TRUE.equals(order.getIsActive()))
      throw new OrderDomainException("Sales order not active", 409);
    List<Requirement> requirements =
        lines
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(tenantId, orderId)
            .stream()
            .map(
                line -> {
                  boolean untyped =
                      line.getModuleSpecs() != null && !line.getModuleSpecs().isEmpty();
                  var profile = line.getRequirementProfileSnapshot();
                  var values =
                      new RequirementValues(
                          line.getModuleSpecs(),
                          line.getModuleType(),
                          line.getRecipeId(),
                          line.getProductDesc(),
                          line.getShippedQty(),
                          line.getLineStatus());
                  return new Requirement(
                      line.getId(),
                      line.getVersion(),
                      line.getProductId(),
                      line.getCreatedAt(),
                      line.getRequestedQty(),
                      line.getUnit(),
                      profile != null && profile.complete(),
                      profile == null
                          ? (untyped ? "UNTYPED_REQUIREMENTS" : "REQUIREMENT_COMPLETENESS_UNKNOWN")
                          : (profile.complete()
                              ? null
                              : String.join(",", profile.incompleteReasons())),
                      profile == null ? OrderCoverFingerprint.of(values) : profile.fingerprint(),
                      RequirementEvidenceProfileMapper.toPort(profile),
                      new Source(
                          "SALES_ORDER_LINE",
                          line.getId(),
                          line.getVersion(),
                          null,
                          line.getCreatedAt(),
                          SourceKnowledge.VERIFIED,
                          null));
                })
            .toList();
    return new Requirements(tenantId, orderId, caseId, order.getVersion(), requirements);
  }

  private record RequirementValues(
      Map<String, Object> moduleSpecs,
      ModuleType moduleType,
      UUID recipeId,
      String description,
      java.math.BigDecimal shippedQty,
      SalesOrderLineStatus status) {}

  static String fingerprint(Requirements requirements, Inputs inputs) {
    var orderedRequirements =
        new Requirements(
            requirements.tenantId(),
            requirements.orderId(),
            requirements.caseId(),
            requirements.orderVersion(),
            requirements.lines().stream()
                .sorted(Comparator.comparing(line -> line.lineId().toString()))
                .toList());
    var orderedInputs =
        new Inputs(
            inputs.demands().stream()
                .sorted(Comparator.comparing(demand -> demand.lineId().toString()))
                .toList(),
            inputs.lots().stream()
                .sorted(Comparator.comparing(lot -> lot.lotId().toString()))
                .toList());
    return OrderCoverFingerprint.of(
        List.of(OrderCoverEvidenceEvaluator.RULE_VERSION, orderedRequirements, orderedInputs));
  }
}
