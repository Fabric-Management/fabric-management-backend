package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Missing;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Verification;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseMode;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditApplier.Applied;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditApplier.Mine;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.FieldInstruction;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.LineOperation;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.Parsed;
import com.fabricmanagement.sales.salesorder.domain.OrderEditBase;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Conflict;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.RecordedConflict;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation.ConflictRecord;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation.Identity;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation.LineIdMapping;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ProfileRef;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import com.fabricmanagement.sales.salesorder.domain.OrderFieldChange;
import com.fabricmanagement.sales.salesorder.domain.OrderFieldChange.ChangeKind;
import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditConflict;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditConflictProblem;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditConflictReason;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLineIdMapping;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOperationView;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResolutionChoice;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderEditBaseRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderEditOperationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFieldChangeRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.RequirementProfileVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The safe edit of a draft sales order (CEDIT-02, CEDIT-03 §4): server bases, merged saves with
 * receipts, conflicts answered with a new base, and the field history written with the change.
 *
 * <p>A save runs in one {@code READ COMMITTED} transaction with an explicit boundary. A conflict is
 * not an exception inside it: its base and receipt commit, and only then is the 409 raised. A
 * domain or validation failure rolls the whole save back, receipt included. Saves are started
 * outside any transaction: the one retry after a lost receipt race needs a fresh transaction.
 *
 * <p>Field leases (CEDIT-07 §3.3): a new save proves, inside its transaction and after the order
 * row is locked, that its edit session holds every key it writes; the server computes the keys from
 * what the save sends, a key list from the client grants nothing. A missing lease answers 409
 * {@code EDIT_LEASE_REQUIRED} and records nothing. An applied or unchanged save releases the leases
 * it used in the same transaction; a conflict or a validation failure releases none. A repeat of a
 * recorded save answers from its receipt before any lease is looked at, and neither checks nor
 * releases a lease. The lease proof (edit session id and tokens) is not part of the request
 * fingerprint: re-acquiring a key and sending the same save again with the same operation id is the
 * same save.
 */
@Service
public class SalesOrderEditService {

  private static final String EDIT_CONFLICT = "EDIT_CONFLICT";
  private static final String EDIT_BASE_EXPIRED = "EDIT_BASE_EXPIRED";
  static final String EDIT_LEASE_REQUIRED = "EDIT_LEASE_REQUIRED";
  static final String EDIT_LEASE_TOKEN_UNEXPECTED = "EDIT_LEASE_TOKEN_UNEXPECTED";

  private final SalesOrderEditBases bases;
  private final SalesOrderEditApplier applier;
  private final SalesOrderAccessPolicy accessPolicy;
  private final SalesOrderRevision revision;
  private final OrderEditBaseRepository baseRepository;
  private final OrderEditOperationRepository operations;
  private final OrderFieldChangeRepository fieldChanges;
  private final RequirementProfileVersionRepository profileVersions;
  private final SalesOrderEditProperties properties;
  private final SalesOrderEditAccess editAccess;
  private final LiveEditLeaseService leases;
  private final SalesOrderEditLeaseService leaseViews;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final TransactionTemplate readCommitted;
  private final TransactionTemplate readOnly;

  public SalesOrderEditService(
      SalesOrderEditBases bases,
      SalesOrderEditApplier applier,
      SalesOrderAccessPolicy accessPolicy,
      SalesOrderRevision revision,
      OrderEditBaseRepository baseRepository,
      OrderEditOperationRepository operations,
      OrderFieldChangeRepository fieldChanges,
      RequirementProfileVersionRepository profileVersions,
      SalesOrderEditProperties properties,
      SalesOrderEditAccess editAccess,
      LiveEditLeaseService leases,
      SalesOrderEditLeaseService leaseViews,
      ObjectMapper objectMapper,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.bases = bases;
    this.applier = applier;
    this.accessPolicy = accessPolicy;
    this.revision = revision;
    this.baseRepository = baseRepository;
    this.operations = operations;
    this.fieldChanges = fieldChanges;
    this.profileVersions = profileVersions;
    this.properties = properties;
    this.editAccess = editAccess;
    this.leases = leases;
    this.leaseViews = leaseViews;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.readCommitted = new TransactionTemplate(transactionManager);
    this.readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  /** What one save transaction answered: a saved result, or a recorded conflict to raise. */
  private sealed interface Answer permits Saved, Conflicted {}

  private record Saved(SalesOrderEditResult result) implements Answer {}

  private record Conflicted(String code, JsonNode body) implements Answer {}

  /** Opens the edit form: a new server base for the actor (CEDIT-02 §3). */
  public SalesOrderEditBase openBase(UUID orderId, UUID actor, Authentication authentication) {
    return bases.open(orderId, actor, authentication);
  }

  /**
   * Saves explicit changes against a server base (CEDIT-02 §4–§5). Returns the applied or unchanged
   * result; raises {@link SalesOrderEditConflictException} with the recorded problem after the
   * conflict's base and receipt committed.
   */
  public SalesOrderEditResult save(
      UUID orderId,
      SalesOrderEditRequest request,
      UUID actor,
      Authentication authentication,
      String path) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("A safe-edit save opens its own transaction");
    }
    Parsed parsed =
        SalesOrderEditInstructions.parse(orderId, request, properties.getMaxLineOperations());
    LeaseProof proof = LeaseProof.of(request);
    Answer answer;
    try {
      answer =
          readCommitted.execute(
              status -> saveOnce(orderId, parsed, proof, actor, authentication, path));
    } catch (DataIntegrityViolationException failure) {
      if (!isOperationRace(failure)) {
        throw failure;
      }
      // Another save recorded this operation id first and its transaction has ended: this one is
      // rolled back as a whole. Judged again in a new transaction it replays, or is refused.
      answer =
          readCommitted.execute(
              status -> saveOnce(orderId, parsed, proof, actor, authentication, path));
    }
    return switch (answer) {
      case Saved saved -> saved.result();
      case Conflicted conflicted ->
          throw new SalesOrderEditConflictException(conflicted.code(), conflicted.body());
    };
  }

  /** The recorded outcome of one of the actor's own saves on this order; otherwise not found. */
  public SalesOrderEditOperationView operation(UUID orderId, UUID operationId, UUID actor) {
    return readOnly.execute(
        status -> {
          UUID tenantId = TenantContext.requireTenantId();
          bases.readable(tenantId, orderId, actor);
          OrderEditOperation receipt =
              operations
                  .findByTenantIdAndOperationId(tenantId, operationId)
                  .filter(found -> found.getSalesOrderId().equals(orderId))
                  .filter(found -> found.getActorId().equals(actor))
                  .orElseThrow(() -> new NotFoundException("Edit operation not found"));
          return new SalesOrderEditOperationView(
              receipt.getOperationId(),
              SalesOrderEditOutcome.valueOf(receipt.getOutcome().name()),
              receipt.getRecordedAt(),
              receipt.getResultVersion(),
              lineIds(receipt.getLineIds()),
              receipt.getOutcome() == OrderEditOperation.Outcome.CONFLICT
                  ? receipt.getResultBaseId()
                  : null);
        });
  }

  // ── the save transaction ─────────────────────────────────────────────────

  private Answer saveOnce(
      UUID orderId,
      Parsed parsed,
      LeaseProof proof,
      UUID actor,
      Authentication authentication,
      String path) {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();

    // A suspended tenant or a deactivated user saves nothing with an older access token (CEDIT-07).
    editAccess.requireActiveCaller(orderId, actor);
    SalesOrder order = bases.readable(tenantId, orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order, PermissionFreshness.FRESH)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    // The order row is the first lock of every content writer (CEDIT-02 §5.5); the entity is
    // reloaded with it, so what follows decides on committed state.
    revision.lockFresh(order);
    if (!Boolean.TRUE.equals(order.getIsActive())) {
      throw new NotFoundException("Sales order not found: " + orderId);
    }
    if (!accessPolicy.canWrite(tenantId, actor, order, PermissionFreshness.CACHED)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }

    Optional<OrderEditOperation> recorded =
        operations.lockByTenantIdAndOperationId(tenantId, parsed.operationId());
    if (recorded.isPresent()) {
      return replay(order, recorded.get(), parsed, actor, authentication, now);
    }

    OrderEditBase base = usableBase(tenantId, parsed.baseId(), orderId, actor);
    SalesOrderEditBases.assertEditable(order);
    // Leases after the order row and before the line rows (CEDIT-07 §3.3): the session row and the
    // lease rows stay locked until this save ends, so no key changes hands before it commits.
    Verification held = verifyLeases(order, base, parsed, proof, actor);
    List<SalesOrderLine> lockedLines = revision.lockFreshLines(order);
    OrderEditSnapshot current = bases.project(order, lockedLines);

    // The receipt and the request's own instructions are matched before anything is resolved
    // against this base: a USE_MINE that sends the recorded instruction again takes the shown mine
    // as it is (§5.7), so no resolution against the newer base can refuse or change it. A decision
    // an earlier round carried past a conflict on another key is kept the same way (CEDIT-04 R1).
    Optional<OrderEditOperation> origin = originOperation(tenantId, base);
    List<OrderEditOperation.Item> originConflicts =
        origin.map(OrderEditOperation::conflictItems).orElse(List.of());
    Decisions decisions =
        decisions(
            originConflicts,
            origin.map(OrderEditOperation::carriedDecisions).orElse(List.of()),
            parsed);
    Mine requested = applier.mine(parsed, base.getContent(), decisions.sameInstruction());
    applier.assertNewClientLines(orderId, parsed);
    OrderEditMerge.resolutionMismatch(
            originConflicts.stream().map(OrderEditOperation.Item::toRecorded).toList(),
            parsed.resolutions(),
            requested.instructions())
        .ifPresent(
            slot -> {
              throw SalesOrderEditInstructions.invalid(
                  "RESOLUTION_MISMATCH",
                  "The resolutions do not decide the conflicts of the base this save was made"
                      + " against",
                  slot.key(),
                  slot.lineId(),
                  slot.clientLineId());
            });
    // A USE_MINE proven by the key's equality instead also saves the mine as shown.
    Mine mine = applier.withRecordedMines(requested, decisions.chosen());

    boolean expired = base.isExpiredAt(now);
    OrderEditMerge.Result merged =
        OrderEditMerge.merge(
            base.getContent(), current, base.guardMap(), mine.instructions(), expired);
    Map<Slot, String> guard = OrderEditMerge.guardBetween(base.getContent(), current);
    Identity identity =
        new Identity(parsed.operationId(), orderId, actor, base.getId(), parsed.fingerprint(), now);

    // An expired base lists every change for review (CEDIT-02 §5.1); with nothing to change it is
    // a no-change like any other.
    if (merged.hasConflicts()) {
      return conflict(
          order,
          lockedLines,
          current,
          base,
          merged,
          mine,
          decisions,
          guard,
          identity,
          expired,
          actor,
          authentication,
          path,
          now);
    }

    if (!merged.hasChanges()) {
      OrderEditBase next =
          bases.derived(
              orderId,
              actor,
              current,
              OrderEditBase.Origin.SAVED,
              base.getId(),
              parsed.operationId(),
              guard,
              now);
      operations.saveAndFlush(
          OrderEditOperation.noChange(identity, order.getVersion(), next.getId()));
      leases.releaseHeld(held.held());
      return new Saved(
          new SalesOrderEditResult(
              parsed.operationId(),
              SalesOrderEditOutcome.NO_CHANGE,
              false,
              order.getVersion(),
              List.of(),
              SalesOrderEditBases.toDto(
                  next, bases.view(order, lockedLines, actor, authentication))));
    }

    long versionBefore = order.getVersion();
    Applied applied = applier.apply(order, lockedLines, merged, mine, actor, now);
    long resultVersion = revision.advanceOnce(order, versionBefore);
    OrderEditSnapshot after = bases.project(order, applied.activeLines());
    OrderEditBase next =
        bases.derived(
            orderId,
            actor,
            after,
            OrderEditBase.Origin.SAVED,
            base.getId(),
            parsed.operationId(),
            guard,
            now);
    OrderEditOperation receipt =
        operations.saveAndFlush(
            OrderEditOperation.applied(
                identity, resultVersion, next.getId(), applied.addedLines()));
    recordHistory(
        new OrderFieldChange.Context(
            orderId, parsed.operationId(), receipt.getId(), actor, resultVersion, now),
        parsed,
        decisions.choices(),
        merged,
        current,
        after,
        applied);
    leases.releaseHeld(held.held());
    return new Saved(
        new SalesOrderEditResult(
            parsed.operationId(),
            SalesOrderEditOutcome.APPLIED,
            false,
            resultVersion,
            lineIds(applied.addedLines()),
            SalesOrderEditBases.toDto(
                next, bases.view(order, applied.activeLines(), actor, authentication))));
  }

  /**
   * Answers a repeated operation from its receipt, before the editable state or the base's expiry
   * are checked (CEDIT-03 §4.3): a save that succeeded is recognised even after the order left the
   * draft. Another order, another actor or other content under the same id is refused.
   */
  private Answer replay(
      SalesOrder order,
      OrderEditOperation receipt,
      Parsed parsed,
      UUID actor,
      Authentication authentication,
      Instant now) {
    UUID tenantId = TenantContext.requireTenantId();
    if (!receipt.isRepeatOf(order.getId(), actor, parsed.fingerprint())) {
      throw OrderDomainException.conflict(
          "OPERATION_ID_REUSED", "This operation id was already used for another save");
    }
    if (receipt.getOutcome() == OrderEditOperation.Outcome.CONFLICT) {
      // The same conflict, with the same base to resolve against; gone with cleanup → unknown.
      usableBase(tenantId, receipt.getResultBaseId(), order.getId(), actor);
      ConflictRecord conflict = receipt.getConflict();
      return new Conflicted(conflict.code(), conflict.problem());
    }
    List<SalesOrderLine> lockedLines = revision.lockFreshLines(order);
    OrderEditSnapshot current = bases.project(order, lockedLines);
    Optional<OrderEditBase> resultBase =
        baseRepository.findForUse(tenantId, receipt.getResultBaseId(), order.getId(), actor);
    // The client's form holds the save's result; what others changed since is guarded.
    Map<Slot, String> guard =
        resultBase
            .map(saved -> OrderEditMerge.guardBetween(saved.getContent(), current))
            .orElse(Map.of());
    OrderEditBase next =
        bases.derived(
            order.getId(),
            actor,
            current,
            OrderEditBase.Origin.SAVED,
            resultBase.map(OrderEditBase::getId).orElse(null),
            receipt.getOperationId(),
            guard,
            now);
    return new Saved(
        new SalesOrderEditResult(
            receipt.getOperationId(),
            SalesOrderEditOutcome.valueOf(receipt.getOutcome().name()),
            true,
            receipt.getResultVersion(),
            lineIds(receipt.getLineIds()),
            SalesOrderEditBases.toDto(
                next, bases.view(order, lockedLines, actor, authentication))));
  }

  /** The edit session and lease tokens a save carries as proof; neither is part of its identity. */
  private record LeaseProof(UUID sessionId, Set<UUID> tokens) {

    static LeaseProof of(SalesOrderEditRequest request) {
      List<UUID> tokens = request.getLeaseTokens() == null ? List.of() : request.getLeaseTokens();
      if (!tokens.isEmpty() && request.getEditSessionId() == null) {
        throw SalesOrderEditInstructions.invalidAt(
            "editSessionId",
            "Lease tokens come with the edit session that holds them",
            null,
            null,
            null);
      }
      return new LeaseProof(request.getEditSessionId(), Set.copyOf(tokens));
    }
  }

  /**
   * The lines this save names that its server base knows and the order no longer has as active
   * lines (CEDIT-07-F1). Read under the order row lock, which a writer removing a line takes first,
   * and without locking the line rows: their locks still come after the leases. An id the base does
   * not know (made up, another order's or added since) is never gone: it keeps its lease
   * requirement and the base's own checks.
   */
  private Set<UUID> goneSinceBase(SalesOrder order, OrderEditBase base, Parsed parsed) {
    List<UUID> named =
        parsed.lines().stream()
            .map(LineOperation::lineId)
            .filter(lineId -> lineId != null && base.getContent().line(lineId).isPresent())
            .toList();
    if (named.isEmpty()) {
      return Set.of();
    }
    Set<UUID> active = revision.activeLineIds(order);
    return named.stream().filter(lineId -> !active.contains(lineId)).collect(Collectors.toSet());
  }

  /**
   * Checks that the save holds every key it writes (CEDIT-07 §3.3). Enforced: each key needs this
   * tab's lease and its token. Off: no proof is needed, but a key somebody still holds is refused,
   * so lowering the mode never frees a held key. A token that proves none of the keys is a client
   * error (422); a missing lease is 409 with the keys to acquire again. Nothing is recorded.
   *
   * <p>A line of the save's base that is no longer an active line of the order needs no lease
   * (CEDIT-07-F1): nobody can acquire it, and the merge answers it (an UPDATE conflicts with the
   * removal, a REMOVE is no change). A token sent for it proves no key and stays unexpected.
   */
  private Verification verifyLeases(
      SalesOrder order, OrderEditBase base, Parsed parsed, LeaseProof proof, UUID actor) {
    LiveLeaseMode mode = leases.mode(SalesOrderLiveRevisionSource.RESOURCE_TYPE);
    Verification verification =
        leases.verify(
            SalesOrderLiveRevisionSource.resource(order.getId()),
            order.getEditEpoch(),
            mode,
            proof.sessionId(),
            proof.tokens(),
            actor,
            SalesOrderLeaseKeys.required(parsed, goneSinceBase(order, base, parsed)));
    if (!verification.missing().isEmpty()) {
      List<SalesOrderEditLeaseDtos.Requirement> requirements =
          verification.missing().stream()
              .map(missing -> requirement(missing, proof, actor))
              .toList();
      throw OrderDomainException.conflict(
              EDIT_LEASE_REQUIRED,
              "Some of the fields you changed are not held by this form; take them again and save")
          .withDetail("leases", requirements);
    }
    if (!verification.unexpectedTokens().isEmpty()) {
      throw OrderDomainException.invalid(
              EDIT_LEASE_TOKEN_UNEXPECTED,
              "Send only the lease tokens of the fields this save changes")
          .withDetail("leaseTokens", List.copyOf(verification.unexpectedTokens()));
    }
    return verification;
  }

  private SalesOrderEditLeaseDtos.Requirement requirement(
      Missing missing, LeaseProof proof, UUID actor) {
    SalesOrderEditLeaseKey key = SalesOrderLeaseKeys.toWire(missing.key());
    boolean another =
        missing.holder() != null && !missing.holder().getSessionId().equals(proof.sessionId());
    return new SalesOrderEditLeaseDtos.Requirement(
        key.key(),
        key.lineId(),
        another
            ? SalesOrderEditLeaseDtos.RequirementReason.HELD_BY_ANOTHER
            : SalesOrderEditLeaseDtos.RequirementReason.NOT_HELD,
        another ? leaseViews.holders(List.of(missing.holder()), actor).getFirst() : null);
  }

  /** The base a save names, on its own order and by its own actor; anything else is unknown. */
  private OrderEditBase usableBase(UUID tenantId, UUID baseId, UUID orderId, UUID actor) {
    return baseRepository
        .findForUse(tenantId, baseId, orderId, actor)
        .orElseThrow(
            () ->
                OrderDomainException.conflict(
                    "EDIT_BASE_UNKNOWN",
                    "The edit base is unknown or no longer kept; reopen the order"));
  }

  /**
   * The shown mines of USE_MINE resolutions that send exactly the instruction the conflict
   * recorded, by slot. Whether the resolutions decide the conflicts correctly is still checked
   * afterwards, against what the request then asks.
   */
  private static Map<Slot, OrderEditOperation.RecordedMine> sameInstructionMines(
      List<OrderEditOperation.Item> conflicts, Parsed parsed) {
    Map<Slot, String> sent = SalesOrderEditInstructions.instructionTokens(parsed);
    Map<Slot, OrderEditOperation.RecordedMine> chosen = new LinkedHashMap<>();
    for (OrderEditOperation.Item item : conflicts) {
      if (item.mine() != null
          && item.instructionToken() != null
          && parsed.resolutions().get(item.slot()) == Choice.USE_MINE
          && item.instructionToken().equals(sent.get(item.slot()))) {
        chosen.put(item.slot(), item.mine());
      }
    }
    return chosen;
  }

  /** The shown mines a save's USE_MINE resolutions choose, by slot. */
  private static Map<Slot, OrderEditOperation.RecordedMine> chosenMines(
      List<OrderEditOperation.Item> conflicts, Parsed parsed) {
    Map<Slot, OrderEditOperation.RecordedMine> chosen = new LinkedHashMap<>();
    for (OrderEditOperation.Item item : conflicts) {
      if (item.mine() != null && parsed.resolutions().get(item.slot()) == Choice.USE_MINE) {
        chosen.put(item.slot(), item.mine());
      }
    }
    return chosen;
  }

  /**
   * The decisions a save carries (CEDIT-04 R1): its own resolutions, and the earlier decisions its
   * base's conflict carried that it sends again unchanged without deciding them anew; the shown
   * mines it takes as recorded, by sending the recorded instruction again ({@code sameInstruction})
   * or by the key's equality ({@code chosen}); and the instruction it sent per slot.
   */
  private record Decisions(
      Map<Slot, Choice> choices,
      Map<Slot, OrderEditOperation.RecordedMine> sameInstruction,
      Map<Slot, OrderEditOperation.RecordedMine> chosen,
      Map<Slot, String> sent) {

    /**
     * The decisions to carry past a conflict that asks only other slots again: each one this save
     * applied with an instruction, outside the slots asked again, with the shown mine it applied.
     * KEEP_CURRENT sends no instruction, so there is nothing to carry.
     */
    List<OrderEditOperation.Carried> carriedPast(Collection<Slot> asked) {
      List<OrderEditOperation.Carried> carried = new ArrayList<>();
      choices.forEach(
          (slot, choice) -> {
            String instruction = sent.get(slot);
            if (choice == Choice.KEEP_CURRENT || instruction == null || asked.contains(slot)) {
              return;
            }
            OrderEditOperation.RecordedMine mine =
                choice != Choice.USE_MINE
                    ? null
                    : chosen.containsKey(slot) ? chosen.get(slot) : sameInstruction.get(slot);
            carried.add(OrderEditOperation.Carried.of(slot, choice, instruction, mine));
          });
      return carried;
    }
  }

  private static Decisions decisions(
      List<OrderEditOperation.Item> conflicts,
      List<OrderEditOperation.Carried> carried,
      Parsed parsed) {
    Map<Slot, String> sent = SalesOrderEditInstructions.instructionTokens(parsed);
    Map<Slot, Choice> choices = new LinkedHashMap<>();
    Map<Slot, OrderEditOperation.RecordedMine> sameInstruction = new LinkedHashMap<>();
    for (OrderEditOperation.Carried decision : carried) {
      Slot slot = decision.slot();
      // Changed since, or decided again: the request's own instruction and resolution stand.
      if (parsed.resolutions().containsKey(slot)
          || !decision.instructionToken().equals(sent.get(slot))) {
        continue;
      }
      choices.put(slot, decision.choice());
      if (decision.choice() == Choice.USE_MINE && decision.mine() != null) {
        sameInstruction.put(slot, decision.mine());
      }
    }
    choices.putAll(parsed.resolutions());
    sameInstruction.putAll(sameInstructionMines(conflicts, parsed));
    return new Decisions(choices, sameInstruction, chosenMines(conflicts, parsed), sent);
  }

  /** The conflict a base was answered with, whose conflicts a save against it must resolve. */
  private Optional<OrderEditOperation> originOperation(UUID tenantId, OrderEditBase base) {
    if (base.getOrigin() != OrderEditBase.Origin.CONFLICT
        && base.getOrigin() != OrderEditBase.Origin.EXPIRED) {
      return Optional.empty();
    }
    return Optional.of(
        operations
            .lockByTenantIdAndOperationId(tenantId, base.getOriginOperationId())
            .orElseThrow(
                () ->
                    OrderDomainException.internal(
                        "EDIT_BASE_ORIGIN_MISSING",
                        "The conflict that produced the edit base is no longer recorded")));
  }

  /**
   * Records a save that saved nothing: the current state as the base to resolve against, and a
   * receipt with the exact problem body, so a repeat answers the same.
   */
  private Answer conflict(
      SalesOrder order,
      List<SalesOrderLine> lockedLines,
      OrderEditSnapshot current,
      OrderEditBase base,
      OrderEditMerge.Result merged,
      Mine mine,
      Decisions decisions,
      Map<Slot, String> guard,
      Identity identity,
      boolean expired,
      UUID actor,
      Authentication authentication,
      String path,
      Instant now) {
    String code = expired ? EDIT_BASE_EXPIRED : EDIT_CONFLICT;
    OrderEditBase conflictBase =
        bases.derived(
            order.getId(),
            actor,
            current,
            expired ? OrderEditBase.Origin.EXPIRED : OrderEditBase.Origin.CONFLICT,
            base.getId(),
            identity.operationId(),
            guard,
            now);
    SalesOrderEditBase currentBase =
        SalesOrderEditBases.toDto(
            conflictBase, bases.view(order, lockedLines, actor, authentication));

    Map<String, RequirementProfileSnapshot> resolvedProfiles = mine.resolvedByFingerprint();
    List<SalesOrderEditConflict> shown = new ArrayList<>();
    List<OrderEditOperation.Item> items = new ArrayList<>();
    for (Conflict conflict : merged.conflicts()) {
      shown.add(
          new SalesOrderEditConflict(
              conflict.slot().key(),
              conflict.slot().lineId(),
              conflict.slot().clientLineId(),
              SalesOrderEditConflictReason.valueOf(conflict.reason().name()),
              display(conflict.base(), resolvedProfiles),
              display(conflict.current(), resolvedProfiles),
              display(conflict.mine(), resolvedProfiles),
              conflict.choices().stream()
                  .map(choice -> SalesOrderEditResolutionChoice.valueOf(choice.name()))
                  .toList()));
      items.add(
          OrderEditOperation.Item.of(
              new RecordedConflict(
                  conflict.slot(),
                  conflict.reason(),
                  conflict.choices(),
                  conflict.mineToken(),
                  conflict.instructionToken()),
              applier.recordedMine(conflict.slot(), mine)));
    }
    SalesOrderEditConflictProblem problem =
        SalesOrderEditConflictProblem.of(
            code,
            expired
                ? "The edit base expired; review your changes against the current order"
                : "Some changes were made by someone else since you opened the order",
            path,
            identity.operationId(),
            base.getId(),
            currentBase,
            shown);
    // Through JSON text, as a repeat reads it back from the receipt: both answers are the same.
    JsonNode body;
    try {
      body = objectMapper.readTree(objectMapper.writeValueAsString(problem));
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException("The conflict problem cannot be written as JSON", failure);
    }
    // Nothing was written: what this save decided outside the conflicts asked again is kept for
    // the save that resolves them (CEDIT-04 R1).
    List<OrderEditOperation.Carried> carried =
        decisions.carriedPast(merged.conflicts().stream().map(Conflict::slot).toList());
    operations.saveAndFlush(
        OrderEditOperation.conflict(
            identity, conflictBase.getId(), new ConflictRecord(code, items, body, carried)));
    return new Conflicted(code, body);
  }

  /**
   * The field history of an applied save, in the save's transaction (CEDIT-02 §6, §7.1): each
   * changed key with the value before and after, whole lines added or removed with their
   * projection, and the resolution that decided the change with its scope. A changed line key that
   * was not decided on its own carries the decision of its whole line (a product change seen and
   * accepted), so the history keeps it after the receipt is cleaned up. A decision carried past a
   * conflict on another key is recorded as the save's own (CEDIT-04 R1).
   */
  private void recordHistory(
      OrderFieldChange.Context context,
      Parsed parsed,
      Map<Slot, Choice> resolutions,
      OrderEditMerge.Result merged,
      OrderEditSnapshot before,
      OrderEditSnapshot after,
      Applied applied) {
    List<OrderFieldChange> changes = new ArrayList<>();

    merged
        .headerChanges()
        .keySet()
        .forEach(
            key ->
                changes.add(
                    OrderFieldChange.record(
                        context,
                        null,
                        key.wireName(),
                        kind(parsed.header().get(key)),
                        json(before.header().value(key)),
                        json(after.header().value(key)),
                        resolution(resolutions, Slot.header(key), null))));

    Map<UUID, LineOperation> updates = new HashMap<>();
    parsed
        .lines()
        .forEach(
            operation -> {
              if (operation.lineId() != null) {
                updates.put(operation.lineId(), operation);
              }
            });
    merged
        .lineChanges()
        .forEach(
            (lineId, keys) -> {
              OrderEditSnapshot.Line old = before.line(lineId).orElseThrow();
              OrderEditSnapshot.Line now = after.line(lineId).orElseThrow();
              LineOperation operation = updates.get(lineId);
              keys.keySet()
                  .forEach(
                      key ->
                          changes.add(
                              OrderFieldChange.record(
                                  context,
                                  lineId,
                                  key.wireName(),
                                  kind(operation.fields().get(key)),
                                  json(old.value(key)),
                                  json(now.value(key)),
                                  resolution(
                                      resolutions,
                                      Slot.line(key, lineId),
                                      Slot.wholeLine(lineId)))));
            });

    merged
        .removals()
        .forEach(
            lineId ->
                changes.add(
                    OrderFieldChange.record(
                        context,
                        lineId,
                        OrderEditKey.LINE,
                        ChangeKind.LINE_REMOVED,
                        json(OrderEditMerge.projection(before.line(lineId).orElseThrow())),
                        null,
                        resolution(resolutions, Slot.wholeLine(lineId), null))));

    applied
        .addedByClientId()
        .forEach(
            (clientLineId, line) ->
                changes.add(
                    OrderFieldChange.record(
                        context,
                        line.getId(),
                        OrderEditKey.LINE,
                        ChangeKind.LINE_ADDED,
                        null,
                        json(OrderEditMerge.projection(after.line(line.getId()).orElseThrow())),
                        resolution(resolutions, Slot.addedLine(clientLineId), null))));

    fieldChanges.saveAll(changes);
  }

  /**
   * The resolution recorded with a change: the one of its own slot, else the one of the whole line
   * it belongs to; whole-line slots (an added or removed line) are of line scope.
   */
  private static OrderFieldChange.Resolution resolution(
      Map<Slot, Choice> resolutions, Slot own, Slot wholeLine) {
    Choice choice = resolutions.get(own);
    if (choice != null) {
      return new OrderFieldChange.Resolution(
          choice,
          own.isWholeLine()
              ? OrderFieldChange.ResolutionScope.LINE
              : OrderFieldChange.ResolutionScope.FIELD);
    }
    Choice lineChoice = wholeLine == null ? null : resolutions.get(wholeLine);
    return lineChoice == null
        ? null
        : new OrderFieldChange.Resolution(lineChoice, OrderFieldChange.ResolutionScope.LINE);
  }

  private static ChangeKind kind(FieldInstruction instruction) {
    return instruction != null && instruction.clear() ? ChangeKind.CLEAR : ChangeKind.SET;
  }

  /** A history value; an absent value is stored as SQL NULL. */
  private JsonNode json(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  /**
   * A conflict value as the client sees it: a specification shows its requirement profile itself,
   * read by its pinned version, or as resolved for the save; other values keep their shape.
   */
  private JsonNode display(Object value, Map<String, RequirementProfileSnapshot> resolved) {
    return value == null
        ? NullNode.getInstance()
        : objectMapper.valueToTree(shown(value, resolved));
  }

  private Object shown(Object value, Map<String, RequirementProfileSnapshot> resolved) {
    if (value instanceof SpecificationValue specification) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("moduleType", specification.moduleType());
      view.put("moduleSpecs", specification.moduleSpecs());
      view.put("requirementProfile", profile(specification.requirementProfile(), resolved));
      return view;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> view = new LinkedHashMap<>();
      map.forEach((key, item) -> view.put(String.valueOf(key), shown(item, resolved)));
      return view;
    }
    return value;
  }

  private RequirementProfileSnapshot profile(
      ProfileRef reference, Map<String, RequirementProfileSnapshot> resolved) {
    if (reference == null) {
      return null;
    }
    if (reference.profileId() == null) {
      return resolved.get(reference.fingerprint());
    }
    return profileVersions
        .findByTenantIdAndProfileIdAndProfileVersion(
            TenantContext.requireTenantId(), reference.profileId(), reference.profileVersion())
        .map(RequirementProfileVersion::getSnapshot)
        .orElse(null);
  }

  private static List<SalesOrderEditLineIdMapping> lineIds(List<LineIdMapping> mappings) {
    return mappings.stream()
        .map(mapping -> new SalesOrderEditLineIdMapping(mapping.clientLineId(), mapping.lineId()))
        .toList();
  }

  /** True only for the unique (tenant, operation id) constraint; other failures are not retried. */
  static boolean isOperationRace(DataIntegrityViolationException failure) {
    Throwable cause = failure;
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
          && OrderEditOperationRepository.OPERATION_UNIQUE_CONSTRAINT.equals(
              violation.getConstraintName())) {
        return true;
      }
      String message = cause.getMessage();
      if (message != null
          && message.contains(OrderEditOperationRepository.OPERATION_UNIQUE_CONSTRAINT)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
