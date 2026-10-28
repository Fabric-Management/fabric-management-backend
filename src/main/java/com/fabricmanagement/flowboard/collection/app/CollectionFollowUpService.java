package com.fabricmanagement.flowboard.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.collection.domain.*;
import com.fabricmanagement.flowboard.collection.dto.CollectionFollowUpDtos.*;
import com.fabricmanagement.flowboard.collection.infra.repository.*;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.routing.app.*;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.app.TaskWorkflowRegistry;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Non-financial work: only the authoritative ledger can settle or reopen an episode. */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionFollowUpService {
  private final CollectionFollowUpPort source;
  private final CollectionFollowUpRepository cases;
  private final CollectionContactRepository contacts;
  private final CollectionFollowUpCommandRepository receipts;
  private final TaskRepository tasks;
  private final TaskAssigneeRepository assignees;
  private final TaskVersionLockRepository taskVersions;
  private final TaskProvisioningService provisioning;
  private final BoardRepository boards;
  private final RoutingEvaluationService routing;
  private final RoutingRepository pools;
  private final RoutingEligibilityService eligibility;
  private final Clock clock;
  private final jakarta.validation.Validator validator;

  public void synchronize(UUID obligation) {
    var actor = TenantContext.getCurrentUserId();
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
    try {
      synchronizeCurrent(obligation);
    } finally {
      TenantContext.setCurrentUserId(actor);
    }
  }

  /**
   * Brings one obligation's case in line with the ledger, and says whether it had to. A case that
   * already shows the ledger's facts is left alone, so a periodic scan costs reads only and a
   * budget on what it changes cannot starve the cases behind those already in line. A case nobody
   * holds yet is never in line: routing is tried again.
   *
   * @return {@code true} when the case was changed
   */
  public boolean repair(UUID obligation) {
    source.lock();
    var facts = source.current(obligation);
    var open =
        cases
            .findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant(), obligation)
            .filter(c -> c.getClosedAt() == null);
    boolean inLine =
        facts.closed() ? open.isEmpty() : open.filter(c -> shows(c, facts)).isPresent();
    if (inLine) return false;
    synchronize(obligation);
    return true;
  }

  private static boolean shows(CollectionFollowUp c, CollectionFollowUpPort.Facts facts) {
    return c.getResponsibleUserId() != null
        && Objects.equals(c.getResponsibleUserId(), facts.owner())
        && c.reflects(
            facts.version(),
            facts.phase(),
            facts.dueDate(),
            facts.currency(),
            facts.outstanding(),
            facts.sourceMissing(),
            facts.stale());
  }

  /** The obligations that have an open case, in id order, for a scan that checks them. */
  @Transactional(readOnly = true)
  public List<UUID> openObligations(UUID after, int limit) {
    return cases.openObligationIds(tenant(), after, PageRequest.of(0, limit));
  }

  private void synchronizeCurrent(UUID obligation) {
    source.lock();
    var facts = source.current(obligation);
    var previous = cases.findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant(), obligation);
    if (facts.closed()) {
      previous
          .filter(c -> c.getClosedAt() == null)
          .ifPresent(
              c -> {
                c.close(
                    clock.instant(), facts.cancelled() ? "SOURCE_CANCELLED" : "RECORDED_SETTLED");
                var task = task(c);
                if (task.getClosedAt() == null) {
                  task.closeGovernedExecution(clock.instant());
                  tasks.saveAndFlush(task);
                }
                cases.saveAndFlush(c);
              });
      return;
    }
    var c =
        previous
            .filter(value -> value.getClosedAt() == null)
            .orElseGet(
                () -> {
                  var fresh =
                      CollectionFollowUp.open(
                          obligation, previous.map(CollectionFollowUp::getEpisode).orElse(0) + 1);
                  cases.saveAndFlush(fresh);
                  var board =
                      boards
                          .findByTenantIdAndBoardType(tenant(), BoardType.GLOBAL)
                          .orElseThrow(
                              () ->
                                  conflict(
                                      "COLLECTION_BOARD_MISSING",
                                      "A global board is required for collection work"));
                  // GENERAL is deliberate: this is finance work, never a production-module task.
                  var task =
                      provisioning.createOrSynchronizeActive(
                          new TaskCreation(
                              board.getId(),
                              "Collection follow-up",
                              null,
                              TaskType.COLLECTION_FOLLOW_UP,
                              ModuleType.GENERAL,
                              Priority.MEDIUM,
                              facts.dueDate(),
                              null,
                              "COLLECTION_OBLIGATION",
                              obligation,
                              "COLLECTION_FOLLOW_UP",
                              fresh.getId(),
                              "COLLECTION_FOLLOW_UP:" + obligation + ":" + fresh.getEpisode(),
                              Set.of(new TaskSubject("COLLECTION_OBLIGATION", obligation))));
                  fresh.task(task.getId());
                  return fresh;
                });
    if (c.getCustomerLabel() == null) c.label(source.partnerName(facts.partnerId()));
    routing.evaluate(tenant(), RoutingPoolKey.COLLECTION_FOLLOW_UP, c.getTaskId());
    UUID owner =
        assignees.findAllByTaskIdAndIsActiveTrue(c.getTaskId()).stream()
            .map(TaskAssignee::getUserId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    source.assign(obligation, owner);
    c.assign(owner);
    // Assignment changes the finance version too; never issue a pre-assignment version to the
    // caller.
    var refreshed = source.current(obligation);
    c.observe(
        refreshed.version(),
        refreshed.phase(),
        refreshed.dueDate(),
        refreshed.currency(),
        refreshed.outstanding(),
        refreshed.sourceMissing(),
        refreshed.stale(),
        clock.instant());
    var task = task(c);
    task.updateDeadline(c.actionableOn());
    tasks.saveAndFlush(task);
    cases.saveAndFlush(c);
  }

  public Detail detail(UUID id, UUID actor) {
    source.lock();
    var c = get(id);
    requireRead(c, actor);
    synchronize(c.getObligationId());
    requireRead(c, actor);
    return detail(c, actor);
  }

  private Detail detail(CollectionFollowUp c, UUID actor) {
    var facts = source.current(c.getObligationId());
    var task = task(c);
    boolean ready =
        c.getClosedAt() == null
            && !facts.sourceMissing()
            && !facts.stale()
            && task.getClosedAt() == null;
    boolean holder =
        ready && Objects.equals(c.getResponsibleUserId(), actor) && candidate(actor, c);
    boolean manager = ready && permission(actor, "manage-routing");
    return new Detail(
        c.getId(),
        c.getObligationId(),
        c.getEpisode(),
        c.getVersion(),
        facts.version(),
        task.getId(),
        task.getVersion(),
        c.getResponsibleUserId(),
        c.getResponsibleUserId() == null
            ? null
            : eligibility.user(tenant(), c.getResponsibleUserId()).displayName(),
        facts.partnerId(),
        c.getCustomerLabel(),
        facts.currency(),
        facts.expected().toPlainString(),
        facts.outstanding().toPlainString(),
        facts.missingInstrument().toPlainString(),
        facts.phase(),
        facts.agreedDate(),
        facts.dueDate(),
        c.getPromisedOn(),
        decimal(c.getPromisedAmount()),
        c.getNextFollowUpOn(),
        c.getClosedAt(),
        facts.sourceMissing() || facts.dueDate() == null
            ? null
            : facts.cancelled() || facts.outstanding().signum() == 0
                ? 0L
                : Math.max(
                    0, java.time.temporal.ChronoUnit.DAYS.between(facts.dueDate(), source.today())),
        facts.sourceMissing(),
        facts.stale(),
        clock.instant(),
        List.of(
            new Capability(
                "RECORD_CONTACT",
                holder,
                holder
                    ? null
                    : !ready
                        ? "SOURCE_NOT_ACTIONABLE"
                        : c.getResponsibleUserId() == null ? "UNASSIGNED" : "ASSIGNED_ELSEWHERE",
                null,
                false),
            new Capability(
                "ASSIGN_RESPONSIBLE", manager, manager ? null : "PERMISSION_DENIED", null, false)));
  }

  public PagedResponse<Contact> contacts(UUID id, UUID actor, int page, int size) {
    var c = get(id);
    requireRead(c, actor);
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page");
    return PagedResponse.from(
        contacts
            .findByTenantIdAndFollowUpIdOrderByCreatedAtDescIdDesc(
                tenant(), id, PageRequest.of(page, size))
            .map(
                cn ->
                    new Contact(
                        cn.getId(),
                        cn.getActorId(),
                        cn.getOutcome(),
                        cn.getOccurredAt(),
                        cn.getCreatedAt(),
                        cn.getPromisedOn(),
                        decimal(cn.getPromisedAmount()),
                        cn.getNextFollowUpOn(),
                        cn.getNote())));
  }

  public Result recordContact(UUID id, UUID actor, ContactRequest request) {
    validate(request);
    source.lock();
    var c = get(id);
    requireRead(c, actor);
    requirePermission(actor, "write");
    String hash = CanonicalJsonFingerprint.of(new Fingerprint("RECORD_CONTACT", id, request));
    var replay = replay(request.operationId(), actor, id, hash);
    if (replay.isPresent()) return replay.get();
    synchronize(c.getObligationId());
    requireRead(c, actor);
    var d = detail(c, actor);
    versions(
        c,
        request.expectedVersion(),
        request.expectedObligationVersion(),
        request.expectedTaskVersion(),
        d);
    if (!d.actions().getFirst().allowed())
      throw conflict("COLLECTION_ACTION_BLOCKED", d.actions().getFirst().reason());
    var conversationDay = source.dayOf(request.occurredAt());
    if (request.occurredAt().isAfter(clock.instant()))
      throw invalid("Contact occurrence cannot be in the future");
    if (request.nextFollowUpOn() != null && request.nextFollowUpOn().isBefore(conversationDay))
      throw invalid("Next follow-up cannot precede the conversation date");
    boolean promise = request.outcome() == CollectionContact.Outcome.PROMISE_TO_PAY;
    if (promise && (request.promisedOn() == null || request.promisedAmount() == null))
      throw invalid("A promise requires its date and amount");
    if (!promise && (request.promisedOn() != null || request.promisedAmount() != null))
      throw invalid("Promise fields require PROMISE_TO_PAY");
    if (request.promisedOn() != null && request.promisedOn().isBefore(conversationDay))
      throw invalid("Promise date cannot precede the conversation date");
    if (request.outcome() == CollectionContact.Outcome.FOLLOW_UP_SCHEDULED
        && request.nextFollowUpOn() == null) throw invalid("A scheduled follow-up requires a date");
    if (request.outcome() == CollectionContact.Outcome.DISPUTED
        && (request.note() == null || request.note().isBlank()))
      throw invalid("A disputed collection needs a note");
    java.math.BigDecimal amount = request.promisedAmount();
    if (request.promisedAmount() != null)
      try {
        amount =
            request
                .promisedAmount()
                .setScale(
                    Currency.getInstance(d.currency()).getDefaultFractionDigits(),
                    java.math.RoundingMode.UNNECESSARY);
      } catch (ArithmeticException | IllegalArgumentException error) {
        throw invalid("Promise amount must use the currency precision");
      }
    var saved =
        contacts.saveAndFlush(
            CollectionContact.record(
                id,
                actor,
                request.outcome(),
                request.occurredAt(),
                request.promisedOn(),
                amount,
                request.nextFollowUpOn(),
                request.note()));
    c.contact(request.nextFollowUpOn(), request.promisedOn(), amount);
    cases.saveAndFlush(c);
    var task = task(c);
    task.updateDeadline(c.actionableOn());
    tasks.saveAndFlush(task);
    return remember(request.operationId(), actor, c, hash, saved.getId(), null, null);
  }

  public Result assign(UUID id, UUID actor, AssignRequest request) {
    validate(request);
    source.lock();
    var c = get(id);
    requireRead(c, actor);
    requirePermission(actor, "manage-routing");
    String hash = CanonicalJsonFingerprint.of(new Fingerprint("ASSIGN_RESPONSIBLE", id, request));
    var replay = replay(request.operationId(), actor, id, hash);
    if (replay.isPresent()) return replay.get();
    synchronize(c.getObligationId());
    requireRead(c, actor);
    var d = detail(c, actor);
    versions(
        c,
        request.expectedVersion(),
        request.expectedObligationVersion(),
        request.expectedTaskVersion(),
        d);
    if (c.getClosedAt() != null || d.sourceMissing() || d.stale())
      throw conflict("COLLECTION_ACTION_BLOCKED", "Source is not actionable");
    if (!candidate(request.responsibleUserId(), c))
      throw conflict(
          "COLLECTION_OWNER_INELIGIBLE", "The responsible user is not an eligible pool member");
    var active = assignees.findAllByTaskIdAndIsActiveTrue(c.getTaskId());
    active.forEach(TaskAssignee::delete);
    assignees.saveAllAndFlush(active);
    assignees.saveAndFlush(
        TaskAssignee.assignToUser(c.getTaskId(), request.responsibleUserId(), AssignedBy.MANAGER));
    taskVersions.forceIncrementNow(task(c));
    c.assign(request.responsibleUserId());
    source.assign(c.getObligationId(), request.responsibleUserId());
    cases.saveAndFlush(c);
    return remember(
        request.operationId(), actor, c, hash, null, request.reason(), request.responsibleUserId());
  }

  private Optional<Result> replay(UUID operation, UUID actor, UUID id, String hash) {
    return receipts
        .findByTenantIdAndOperationId(tenant(), operation)
        .map(
            r -> {
              if (!r.getActorId().equals(actor)
                  || !r.getFollowUpId().equals(id)
                  || !r.getFingerprint().equals(hash))
                throw conflict("IDEMPOTENCY_CONFLICT", "Operation key belongs to another command");
              return new Result(id, r.getResultVersion(), r.getContactId(), true);
            });
  }

  private Result remember(
      UUID operation,
      UUID actor,
      CollectionFollowUp c,
      String hash,
      UUID contact,
      String reason,
      UUID owner) {
    receipts.saveAndFlush(
        CollectionFollowUpCommand.record(
            operation, actor, c.getId(), hash, contact, c.getVersion(), reason, owner));
    return new Result(c.getId(), c.getVersion(), contact, false);
  }

  private void versions(CollectionFollowUp c, Long expected, Long obligation, Long task, Detail d) {
    if (!Objects.equals(expected, c.getVersion())
        || !Objects.equals(obligation, d.obligationVersion())
        || !Objects.equals(task, d.taskVersion()))
      throw conflict(
          "COLLECTION_FOLLOW_UP_VERSION_CONFLICT",
          "Reload the current obligation and follow-up before acting");
  }

  private boolean candidate(UUID actor, CollectionFollowUp c) {
    return pools.isActiveMember(tenant(), RoutingPoolKey.COLLECTION_FOLLOW_UP, actor)
        && eligibility
            .eligibility(
                tenant(),
                c.getObligationId(),
                eligibility.user(tenant(), actor),
                true,
                RoutingPoolKey.COLLECTION_FOLLOW_UP)
            .isEmpty();
  }

  private boolean permission(UUID actor, String action) {
    var u = eligibility.user(tenant(), actor);
    return u.active() && tenant().equals(u.tenantId()) && u.permissions().can("flowboard", action);
  }

  private void requirePermission(UUID actor, String action) {
    if (!permission(actor, action))
      throw new AccessDeniedException("Fresh FlowBoard permission is required");
  }

  private void requireRead(CollectionFollowUp c, UUID actor) {
    if (actor == null || !actor.equals(TenantContext.getCurrentUserId()))
      throw new AccessDeniedException("The actor must match the authenticated context");
    requirePermission(actor, "read");
    if (!source.mayRead(actor, c.getObligationId()))
      throw new NotFoundException("Collection follow-up not found");
  }

  private CollectionFollowUp get(UUID id) {
    return cases
        .findByTenantIdAndId(tenant(), id)
        .orElseThrow(() -> new NotFoundException("Collection follow-up not found"));
  }

  private Task task(CollectionFollowUp c) {
    return tasks
        .findById(c.getTaskId())
        .filter(
            t ->
                tenant().equals(t.getTenantId())
                    && t.getTaskType() == TaskType.COLLECTION_FOLLOW_UP
                    && TaskWorkflowRegistry.COLLECTION_DEFINITION_ID.equals(
                        t.getWorkflowDefinitionId())
                    && Integer.valueOf(1).equals(t.getWorkflowVersion()))
        .orElseThrow(() -> new NotFoundException("Collection task not found"));
  }

  private static String decimal(java.math.BigDecimal amount) {
    return amount == null ? null : amount.toPlainString();
  }

  private UUID tenant() {
    return TenantContext.requireTenantId();
  }

  private void validate(Object request) {
    if (!validator.validate(request).isEmpty())
      throw invalid("Invalid collection follow-up command");
  }

  private static FlowBoardDomainException conflict(String code, String detail) {
    return new FlowBoardDomainException(detail, code, 409);
  }

  private static FlowBoardDomainException invalid(String detail) {
    return new FlowBoardDomainException(detail, "COLLECTION_FOLLOW_UP_INVALID", 400);
  }

  private record Fingerprint(String action, UUID id, Object request) {}
}
