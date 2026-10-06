package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.Line;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The pure three-way comparison of the safe edit (CEDIT-02 §5). For every key the request names:
 * mine equal to base keeps the current value, current equal to base takes mine, current equal to
 * mine needs nothing, otherwise the key conflicts. One conflict rejects the whole request; the
 * caller applies nothing then. Equality is the key's own: decimals by value, objects regardless of
 * key order, arrays in order, text exactly, a requirement profile by its semantic fingerprint.
 */
public final class OrderEditMerge {

  private OrderEditMerge() {}

  /** Why a key or a line could not be merged. */
  public enum Reason {
    CHANGED_ON_SERVER,
    LINE_REMOVED_ON_SERVER,
    LINE_CHANGED_ON_SERVER,
    LINE_PRODUCT_CHANGED,
    UNCONFIRMED_REVERT,
    REVIEW_REQUIRED
  }

  /** How the user decides a conflict when saving against the conflict's base. */
  public enum Choice {
    KEEP_CURRENT,
    USE_MINE,
    NEW_VALUE
  }

  private static final List<Choice> ALL_CHOICES =
      List.of(Choice.KEEP_CURRENT, Choice.USE_MINE, Choice.NEW_VALUE);
  private static final List<Choice> KEEP_ONLY = List.of(Choice.KEEP_CURRENT);
  private static final List<Choice> KEEP_OR_MINE = List.of(Choice.KEEP_CURRENT, Choice.USE_MINE);
  private static final List<Choice> KEEP_OR_NEW = List.of(Choice.KEEP_CURRENT, Choice.NEW_VALUE);

  /**
   * Where a conflict, a guarded value or a resolution sits: a header key ({@code lineId} and {@code
   * clientLineId} null), a key of an existing line, a whole existing line (key {@code "line"} with
   * its id) or a whole added line (key {@code "line"} with its client id).
   */
  public record Slot(String key, UUID lineId, UUID clientLineId) {
    public Slot {
      Objects.requireNonNull(key, "Slot key is required");
    }

    public static Slot header(OrderEditKey key) {
      return new Slot(key.wireName(), null, null);
    }

    public static Slot line(OrderEditKey key, UUID lineId) {
      return new Slot(key.wireName(), lineId, null);
    }

    public static Slot wholeLine(UUID lineId) {
      return new Slot(OrderEditKey.LINE, lineId, null);
    }

    public static Slot addedLine(UUID clientLineId) {
      return new Slot(OrderEditKey.LINE, null, clientLineId);
    }

    public boolean isWholeLine() {
      return OrderEditKey.LINE.equals(key);
    }
  }

  /** One line operation of a request, its values already turned into mine. */
  public sealed interface LineInstruction permits Update, Remove, Add {}

  public record Update(UUID lineId, Map<OrderEditKey, Object> mine) implements LineInstruction {
    public Update {
      mine = readOnly(mine);
    }
  }

  public record Remove(UUID lineId) implements LineInstruction {}

  public record Add(UUID clientLineId, UUID productId, Map<OrderEditKey, Object> mine)
      implements LineInstruction {
    public Add {
      mine = readOnly(mine);
    }
  }

  /** A read-only copy that keeps null values: a cleared simple value is a null mine. */
  private static Map<OrderEditKey, Object> readOnly(Map<OrderEditKey, Object> values) {
    return values == null
        ? Map.of()
        : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /**
   * What a request asks: mine per header key, the line operations and the slots its resolutions
   * decide. Map values are never null keys; a cleared simple value is a null map value.
   *
   * <p>{@code instructionTokens} identify what the request itself sent per slot (SET/CLEAR and the
   * wire value; a whole line's operation), before anything was resolved against a base. They are
   * recorded with a conflict for audit and as one proof of USE_MINE: sending the same instruction
   * again chooses the mine the conflict showed, which the server substitutes from its receipt
   * (CEDIT-02 §5.7). A resolution is otherwise judged by the key's own equality of the resolved
   * values, never by raw JSON.
   */
  public record Instructions(
      Map<OrderEditKey, Object> header,
      List<LineInstruction> lines,
      Set<Slot> resolved,
      Map<Slot, String> instructionTokens) {
    public Instructions {
      header = header == null ? Map.of() : java.util.Collections.unmodifiableMap(header);
      lines = lines == null ? List.of() : List.copyOf(lines);
      resolved = resolved == null ? Set.of() : Set.copyOf(resolved);
      instructionTokens = instructionTokens == null ? Map.of() : Map.copyOf(instructionTokens);
    }

    public Instructions(
        Map<OrderEditKey, Object> header, List<LineInstruction> lines, Set<Slot> resolved) {
      this(header, lines, resolved, Map.of());
    }

    /** What the request itself sent for a slot, or null when no instruction token was given. */
    String instructionToken(Slot slot) {
      return instructionTokens.get(slot);
    }
  }

  /**
   * A key or line that was not merged. Values are domain values or line projections. {@code
   * mineToken} is the key's equality token of the resolved mine shown to the user; {@code
   * instructionToken} what the request sent for the slot, or null.
   */
  public record Conflict(
      Slot slot,
      Reason reason,
      Object base,
      Object current,
      Object mine,
      List<Choice> choices,
      String mineToken,
      String instructionToken) {}

  /** A conflict as recorded on its receipt: enough to check a later resolution. */
  public record RecordedConflict(
      Slot slot, Reason reason, List<Choice> choices, String mineToken, String instructionToken) {

    public RecordedConflict(Slot slot, Reason reason, List<Choice> choices, String mineToken) {
      this(slot, reason, choices, mineToken, null);
    }
  }

  /** The outcome of a merge. Nothing is applied by the caller when it has conflicts. */
  public record Result(
      List<Conflict> conflicts,
      Map<OrderEditKey, Object> headerChanges,
      Map<UUID, Map<OrderEditKey, Object>> lineChanges,
      List<UUID> removals,
      List<Add> additions) {

    public boolean hasConflicts() {
      return !conflicts.isEmpty();
    }

    public boolean hasChanges() {
      return !headerChanges.isEmpty()
          || !lineChanges.isEmpty()
          || !removals.isEmpty()
          || !additions.isEmpty();
    }
  }

  /**
   * Merges a request made against {@code base} into {@code current}. When {@code expired}, every
   * change that would apply is listed for review instead (EDIT_BASE_EXPIRED); the caller applies
   * nothing.
   */
  public static Result merge(
      OrderEditSnapshot base,
      OrderEditSnapshot current,
      Map<Slot, String> guard,
      Instructions instructions,
      boolean expired) {
    Map<Slot, String> guarded = guard == null ? Map.of() : guard;
    List<Conflict> conflicts = new ArrayList<>();
    List<Conflict> reviews = new ArrayList<>();
    Map<OrderEditKey, Object> headerChanges = new LinkedHashMap<>();
    Map<UUID, Map<OrderEditKey, Object>> lineChanges = new LinkedHashMap<>();
    List<UUID> removals = new ArrayList<>();
    List<Add> additions = new ArrayList<>();

    instructions.header().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              OrderEditKey key = entry.getKey();
              Slot slot = Slot.header(key);
              Object baseValue = base.header().value(key);
              Object currentValue = current.header().value(key);
              Object mine = entry.getValue();
              Conflict conflict =
                  decide(slot, baseValue, currentValue, mine, guarded, instructions);
              if (conflict != null) {
                conflicts.add(conflict);
              } else if (applies(baseValue, currentValue, mine)) {
                headerChanges.put(key, mine);
                reviews.add(review(slot, baseValue, currentValue, mine, instructions));
              }
            });

    for (LineInstruction instruction : instructions.lines()) {
      switch (instruction) {
        case Update update ->
            mergeUpdate(
                update, base, current, guarded, instructions, conflicts, reviews, lineChanges);
        case Remove remove -> {
          Line baseLine = baseLine(base, remove.lineId());
          Optional<Line> currentLine = current.line(remove.lineId());
          if (currentLine.isEmpty()) {
            // Already removed by someone else: the request's intent holds; nothing to do.
            continue;
          }
          Map<String, Object[]> changed = changedParts(baseLine, currentLine.get());
          Slot slot = Slot.wholeLine(remove.lineId());
          if (!changed.isEmpty()) {
            conflicts.add(
                new Conflict(
                    slot,
                    Reason.LINE_CHANGED_ON_SERVER,
                    side(changed, 0),
                    side(changed, 1),
                    null,
                    KEEP_OR_MINE,
                    lineInstructionToken(remove),
                    instructions.instructionToken(slot)));
          } else {
            removals.add(remove.lineId());
            reviews.add(
                new Conflict(
                    slot,
                    Reason.REVIEW_REQUIRED,
                    projection(baseLine),
                    projection(currentLine.get()),
                    null,
                    KEEP_OR_MINE,
                    lineInstructionToken(remove),
                    instructions.instructionToken(slot)));
          }
        }
        case Add add -> {
          additions.add(add);
          Slot added = Slot.addedLine(add.clientLineId());
          reviews.add(
              new Conflict(
                  added,
                  Reason.REVIEW_REQUIRED,
                  null,
                  null,
                  addProjection(add),
                  ALL_CHOICES,
                  lineInstructionToken(add),
                  instructions.instructionToken(added)));
        }
      }
    }

    if (expired) {
      Set<Slot> listed = new LinkedHashSet<>();
      conflicts.forEach(conflict -> listed.add(conflict.slot()));
      reviews.stream().filter(review -> listed.add(review.slot())).forEach(conflicts::add);
    }
    return new Result(
        List.copyOf(conflicts),
        java.util.Collections.unmodifiableMap(headerChanges),
        java.util.Collections.unmodifiableMap(lineChanges),
        List.copyOf(removals),
        List.copyOf(additions));
  }

  private static void mergeUpdate(
      Update update,
      OrderEditSnapshot base,
      OrderEditSnapshot current,
      Map<Slot, String> guard,
      Instructions instructions,
      List<Conflict> conflicts,
      List<Conflict> reviews,
      Map<UUID, Map<OrderEditKey, Object>> lineChanges) {
    Line baseLine = baseLine(base, update.lineId());
    Optional<Line> found = current.line(update.lineId());
    Slot whole = Slot.wholeLine(update.lineId());
    if (found.isEmpty()) {
      // A removed line is never brought back by an edit made before its removal.
      conflicts.add(
          new Conflict(
              whole,
              Reason.LINE_REMOVED_ON_SERVER,
              projection(baseLine),
              null,
              updateProjection(update),
              KEEP_ONLY,
              lineInstructionToken(update),
              instructions.instructionToken(whole)));
      return;
    }
    Line currentLine = found.get();
    if (!Objects.equals(baseLine.productId(), currentLine.productId())) {
      // A specification with a requirement profile was resolved for the earlier product: it cannot
      // be saved as shown on the corrected line, so only a new value or the current one is offered.
      conflicts.add(
          new Conflict(
              whole,
              Reason.LINE_PRODUCT_CHANGED,
              projection(baseLine),
              projection(currentLine),
              updateProjection(update),
              carriesProfile(update.mine().get(OrderEditKey.LINE_SPECIFICATION))
                  ? KEEP_OR_NEW
                  : ALL_CHOICES,
              lineInstructionToken(update),
              instructions.instructionToken(whole)));
      return;
    }
    Map<OrderEditKey, Object> changes = new LinkedHashMap<>();
    update.mine().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              OrderEditKey key = entry.getKey();
              Slot slot = Slot.line(key, update.lineId());
              Object baseValue = baseLine.value(key);
              Object currentValue = currentLine.value(key);
              Object mine = entry.getValue();
              Conflict conflict = decide(slot, baseValue, currentValue, mine, guard, instructions);
              if (conflict != null) {
                conflicts.add(conflict);
              } else if (applies(baseValue, currentValue, mine)) {
                changes.put(key, mine);
                reviews.add(review(slot, baseValue, currentValue, mine, instructions));
              }
            });
    if (!changes.isEmpty()) {
      lineChanges.put(update.lineId(), changes);
    }
  }

  /** The conflict of one key, or null when the key merges (applies or needs nothing). */
  private static Conflict decide(
      Slot slot,
      Object base,
      Object current,
      Object mine,
      Map<Slot, String> guard,
      Instructions instructions) {
    if (same(mine, base)) {
      return null;
    }
    String mineToken = token(mine);
    String instructionToken = instructions.instructionToken(slot);
    if (same(current, base)) {
      // The auxiliary stale-revert guard (§5.8): mine returns a value another writer replaced
      // between this base's parent and this base. A resolution decides the key instead.
      String parent = guard.get(slot);
      if (parent != null && !instructions.resolved().contains(slot) && parent.equals(mineToken)) {
        return new Conflict(
            slot,
            Reason.UNCONFIRMED_REVERT,
            base,
            current,
            mine,
            ALL_CHOICES,
            mineToken,
            instructionToken);
      }
      return null;
    }
    if (same(current, mine)) {
      return null;
    }
    return new Conflict(
        slot,
        Reason.CHANGED_ON_SERVER,
        base,
        current,
        mine,
        choicesFor(current, mine),
        mineToken,
        instructionToken);
  }

  /**
   * The choices of a changed key. A specification without a requirement profile cannot replace one
   * the server has since given the line (profile versions are append-only), so USE_MINE is not
   * offered then.
   */
  private static List<Choice> choicesFor(Object current, Object mine) {
    if (mine instanceof SpecificationValue mineSpecification
        && current instanceof SpecificationValue currentSpecification
        && !carriesProfile(mineSpecification)
        && carriesProfile(currentSpecification)) {
      return KEEP_OR_NEW;
    }
    return ALL_CHOICES;
  }

  private static boolean carriesProfile(Object value) {
    return value instanceof SpecificationValue specification
        && specification.requirementProfile() != null;
  }

  private static boolean applies(Object base, Object current, Object mine) {
    return !same(mine, base) && same(current, base);
  }

  private static Conflict review(
      Slot slot, Object base, Object current, Object mine, Instructions instructions) {
    return new Conflict(
        slot,
        Reason.REVIEW_REQUIRED,
        base,
        current,
        mine,
        ALL_CHOICES,
        token(mine),
        instructions.instructionToken(slot));
  }

  private static Line baseLine(OrderEditSnapshot base, UUID lineId) {
    return base.line(lineId)
        .orElseThrow(() -> new IllegalArgumentException("Line is not in the base: " + lineId));
  }

  /**
   * The stale-revert guard of a base derived from {@code parent} (§5.8): every key whose value
   * another writer changed between the parent and {@code child}, with the parent's value. Keys of
   * lines present in both are compared; added and removed lines are not guarded.
   */
  public static Map<Slot, String> guardBetween(OrderEditSnapshot parent, OrderEditSnapshot child) {
    Map<Slot, String> guard = new LinkedHashMap<>();
    for (OrderEditKey key : OrderEditKey.values()) {
      if (key.isLineKey()) {
        continue;
      }
      Object before = parent.header().value(key);
      if (!same(before, child.header().value(key))) {
        guard.put(Slot.header(key), token(before));
      }
    }
    for (Line before : parent.lines()) {
      child
          .line(before.lineId())
          .ifPresent(
              after -> {
                for (OrderEditKey key : OrderEditKey.values()) {
                  if (key.isLineKey() && !same(before.value(key), after.value(key))) {
                    guard.put(Slot.line(key, before.lineId()), token(before.value(key)));
                  }
                }
              });
    }
    return guard;
  }

  /**
   * The first slot a request's resolutions do not decide correctly, or empty. Every recorded
   * conflict needs exactly one resolution and no other resolution is accepted; a choice the
   * conflict did not offer is a mismatch. KEEP_CURRENT sends no instruction for the slot. USE_MINE
   * sends the instruction again: either the same instruction as recorded (the server then applies
   * the mine it showed, from its receipt) or one whose value is equal to that mine by the key's own
   * equality. NEW_VALUE sends a value that is not equal to the shown mine by that equality, however
   * its JSON is written (CEDIT-02 §5.7).
   */
  public static Optional<Slot> resolutionMismatch(
      Collection<RecordedConflict> recorded,
      Map<Slot, Choice> resolutions,
      Instructions instructions) {
    Map<Slot, RecordedConflict> bySlot = new LinkedHashMap<>();
    recorded.forEach(conflict -> bySlot.put(conflict.slot(), conflict));
    for (Slot slot : resolutions.keySet()) {
      if (!bySlot.containsKey(slot)) {
        return Optional.of(slot);
      }
    }
    for (RecordedConflict conflict : bySlot.values()) {
      Choice choice = resolutions.get(conflict.slot());
      if (choice == null || !conflict.choices().contains(choice)) {
        return Optional.of(conflict.slot());
      }
      Optional<String> resolved = resolvedToken(conflict.slot(), instructions);
      boolean consistent =
          switch (choice) {
            case KEEP_CURRENT -> resolved.isEmpty();
            case USE_MINE ->
                resolved.isPresent()
                    && (resolved.get().equals(conflict.mineToken())
                        || sameInstruction(conflict, instructions));
            case NEW_VALUE -> resolved.isPresent() && !resolved.get().equals(conflict.mineToken());
          };
      if (!consistent) {
        return Optional.of(conflict.slot());
      }
    }
    return Optional.empty();
  }

  /** True when the request sends exactly the instruction the conflict recorded for its slot. */
  public static boolean sameInstruction(RecordedConflict conflict, Instructions instructions) {
    String sent = instructions.instructionToken(conflict.slot());
    return sent != null && sent.equals(conflict.instructionToken());
  }

  /**
   * The equality token of what the request asks for a slot, resolved against the request's own
   * base, or empty when it asks nothing there.
   */
  static Optional<String> resolvedToken(Slot slot, Instructions instructions) {
    if (slot.isWholeLine()) {
      for (LineInstruction instruction : instructions.lines()) {
        boolean matches =
            switch (instruction) {
              case Update update -> slot.lineId() != null && slot.lineId().equals(update.lineId());
              case Remove remove -> slot.lineId() != null && slot.lineId().equals(remove.lineId());
              case Add add ->
                  slot.clientLineId() != null && slot.clientLineId().equals(add.clientLineId());
            };
        if (matches) {
          return Optional.of(lineInstructionToken(instruction));
        }
      }
      return Optional.empty();
    }
    OrderEditKey key =
        OrderEditKey.fromWireName(slot.key())
            .orElseThrow(() -> new IllegalArgumentException("Unknown edit key: " + slot.key()));
    if (!key.isLineKey()) {
      return instructions.header().containsKey(key)
          ? Optional.of(token(instructions.header().get(key)))
          : Optional.empty();
    }
    for (LineInstruction instruction : instructions.lines()) {
      if (instruction instanceof Update update
          && update.lineId().equals(slot.lineId())
          && update.mine().containsKey(key)) {
        return Optional.of(token(update.mine().get(key)));
      }
    }
    return Optional.empty();
  }

  /** The comparable identity of a whole-line instruction. */
  public static String lineInstructionToken(LineInstruction instruction) {
    Map<String, Object> view = new LinkedHashMap<>();
    switch (instruction) {
      case Update update -> {
        view.put("operation", "UPDATE");
        view.put("fields", fieldView(update.mine()));
      }
      case Remove remove -> view.put("operation", "REMOVE");
      case Add add -> {
        view.put("operation", "ADD");
        view.put("productId", add.productId());
        view.put("fields", fieldView(add.mine()));
      }
    }
    return token(view);
  }

  private static Map<String, Object> fieldView(Map<OrderEditKey, Object> fields) {
    Map<String, Object> view = new LinkedHashMap<>();
    fields.forEach((key, value) -> view.put(key.wireName(), equalityView(value)));
    return view;
  }

  /** True when a line differs from its base in any key, its product or its allocations. */
  public static boolean lineChanged(Line base, Line current) {
    return !changedParts(base, current).isEmpty();
  }

  /** The differing parts of a line: name → {base value, current value}. */
  public static Map<String, Object[]> changedParts(Line base, Line current) {
    Map<String, Object[]> changed = new LinkedHashMap<>();
    if (!Objects.equals(base.productId(), current.productId())) {
      changed.put("productId", new Object[] {base.productId(), current.productId()});
    }
    for (OrderEditKey key : OrderEditKey.values()) {
      if (key.isLineKey() && !same(base.value(key), current.value(key))) {
        changed.put(key.wireName(), new Object[] {base.value(key), current.value(key)});
      }
    }
    if (!Objects.equals(base.allocationDigest(), current.allocationDigest())) {
      changed.put(
          "allocationDigest", new Object[] {base.allocationDigest(), current.allocationDigest()});
    }
    return changed;
  }

  private static Map<String, Object> side(Map<String, Object[]> changed, int index) {
    Map<String, Object> side = new LinkedHashMap<>();
    changed.forEach((name, values) -> side.put(name, values[index]));
    return side;
  }

  /** A line as shown in a conflict: id, product, every key and the allocation digest. */
  public static Map<String, Object> projection(Line line) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("lineId", line.lineId());
    view.put("productId", line.productId());
    for (OrderEditKey key : OrderEditKey.values()) {
      if (key.isLineKey()) {
        view.put(key.wireName(), line.value(key));
      }
    }
    view.put("allocationDigest", line.allocationDigest());
    return view;
  }

  private static Map<String, Object> updateProjection(Update update) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("lineId", update.lineId());
    update.mine().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> view.put(entry.getKey().wireName(), entry.getValue()));
    return view;
  }

  private static Map<String, Object> addProjection(Add add) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("clientLineId", add.clientLineId());
    view.put("productId", add.productId());
    add.mine().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> view.put(entry.getKey().wireName(), entry.getValue()));
    return view;
  }

  /** The key's equality: same values for the purpose of a merge (§5.2). */
  public static boolean same(Object left, Object right) {
    return token(left).equals(token(right));
  }

  /**
   * A stable comparable identity of a value: canonical JSON (sorted object keys, decimals by value,
   * arrays in order, text exact); a specification compares by its profile fingerprint.
   */
  public static String token(Object value) {
    return value == null ? "null" : CanonicalJsonFingerprint.of(equalityView(value));
  }

  private static Object equalityView(Object value) {
    if (value instanceof SpecificationValue specification) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("moduleType", specification.moduleType());
      view.put("moduleSpecs", specification.moduleSpecs());
      view.put("profileFingerprint", specification.profileFingerprint());
      return view;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> view = new LinkedHashMap<>();
      map.forEach((key, item) -> view.put(String.valueOf(key), equalityView(item)));
      return view;
    }
    return value;
  }
}
