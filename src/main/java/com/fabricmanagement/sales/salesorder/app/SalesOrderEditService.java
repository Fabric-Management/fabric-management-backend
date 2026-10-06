package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 */
@Service
public class SalesOrderEditService {

  private static final String EDIT_CONFLICT = "EDIT_CONFLICT";
  private static final String EDIT_BASE_EXPIRED = "EDIT_BASE_EXPIRED";

  private final SalesOrderEditBases bases;
  private final SalesOrderEditApplier applier;
  private final SalesOrderAccessPolicy accessPolicy;
  private final SalesOrderRevision revision;
  private final OrderEditBaseRepository baseRepository;
  private final OrderEditOperationRepository operations;
  private final OrderFieldChangeRepository fieldChanges;
  private final RequirementProfileVersionRepository profileVersions;
  private final SalesOrderEditProperties properties;
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
    Answer answer;
    try {
      answer =
          readCommitted.execute(status -> saveOnce(orderId, parsed, actor, authentication, path));
    } catch (DataIntegrityViolationException failure) {
      if (!isOperationRace(failure)) {
        throw failure;
      }
      // Another save recorded this operation id first and its transaction has ended: this one is
      // rolled back as a whole. Judged again in a new transaction it replays, or is refused.
      answer =
          readCommitted.execute(status -> saveOnce(orderId, parsed, actor, authentication, path));
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
      UUID orderId, Parsed parsed, UUID actor, Authentication authentication, String path) {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();

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
    // Future lease validation belongs here (CEDIT-03 §4.2); none in this ticket.
    List<SalesOrderLine> lockedLines = revision.lockFreshLines(order);
    OrderEditSnapshot current = bases.project(order, lockedLines);

    // The receipt and the request's own instructions are matched before anything is resolved
    // against this base: a USE_MINE that sends the recorded instruction again takes the shown mine
    // as it is (§5.7), so no resolution against the newer base can refuse or change it.
    List<OrderEditOperation.Item> originConflicts = originConflicts(tenantId, base);
    Mine requested =
        applier.mine(parsed, base.getContent(), sameInstructionMines(originConflicts, parsed));
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
    Mine mine = applier.withRecordedMines(requested, chosenMines(originConflicts, parsed));

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
        merged,
        current,
        after,
        applied);
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

  /** The conflicts a base was answered with, which a save against it must resolve. */
  private List<OrderEditOperation.Item> originConflicts(UUID tenantId, OrderEditBase base) {
    if (base.getOrigin() != OrderEditBase.Origin.CONFLICT
        && base.getOrigin() != OrderEditBase.Origin.EXPIRED) {
      return List.of();
    }
    return operations
        .lockByTenantIdAndOperationId(tenantId, base.getOriginOperationId())
        .map(OrderEditOperation::conflictItems)
        .orElseThrow(
            () ->
                OrderDomainException.internal(
                    "EDIT_BASE_ORIGIN_MISSING",
                    "The conflict that produced the edit base is no longer recorded"));
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
    operations.saveAndFlush(
        OrderEditOperation.conflict(
            identity, conflictBase.getId(), new ConflictRecord(code, items, body)));
    return new Conflicted(code, body);
  }

  /**
   * The field history of an applied save, in the save's transaction (CEDIT-02 §6, §7.1): each
   * changed key with the value before and after, whole lines added or removed with their
   * projection, and the resolution that decided the change with its scope. A changed line key that
   * was not decided on its own carries the decision of its whole line (a product change seen and
   * accepted), so the history keeps it after the receipt is cleaned up.
   */
  private void recordHistory(
      OrderFieldChange.Context context,
      Parsed parsed,
      OrderEditMerge.Result merged,
      OrderEditSnapshot before,
      OrderEditSnapshot after,
      Applied applied) {
    List<OrderFieldChange> changes = new ArrayList<>();
    Map<Slot, Choice> resolutions = parsed.resolutions();

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
