package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Reason;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.RecordedConflict;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.RequestedDateValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/**
 * The receipt of one safe-edit save (CEDIT-02 §6), written in the same transaction as what it
 * records. A repeated save with the same operation id is answered from it instead of being applied
 * again; the same id with another order, actor or content is refused. A failed save leaves no
 * receipt. Receipts expire; the field history they correlate with does not.
 */
@Entity
@Immutable
@Table(name = "order_edit_operation", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEditOperation extends BaseEntity {

  public enum Outcome {
    APPLIED,
    NO_CHANGE,
    CONFLICT
  }

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  /** The base the save named. */
  @Column(name = "base_id", nullable = false, updatable = false)
  private UUID baseId;

  @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 20)
  private Outcome outcome;

  /** The order version this save left; null for a conflict. */
  @Column(name = "result_version", updatable = false)
  private Long resultVersion;

  /** The next base of an applied or unchanged save; the current base of a conflict. */
  @Column(name = "result_base_id", nullable = false, updatable = false)
  private UUID resultBaseId;

  @Type(JsonType.class)
  @Column(name = "line_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<LineIdMapping> lineIds;

  @Type(JsonType.class)
  @Column(name = "conflict", updatable = false, columnDefinition = "jsonb")
  private ConflictRecord conflict;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  /** A line the save added: the client's id and the line it became. */
  public record LineIdMapping(UUID clientLineId, UUID lineId) {}

  /**
   * What a conflict answered: its code, the conflicts as a later resolution is checked against
   * them, and the exact problem body, so a repeat answers identically.
   */
  public record ConflictRecord(String code, List<Item> items, JsonNode problem) {
    public ConflictRecord {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  /**
   * One recorded conflict: where it is, why, the choices offered, the equality token of the mine
   * shown, what the request sent for the slot, and the shown mine itself where it was completed
   * from the request's base.
   */
  public record Item(
      String key,
      UUID lineId,
      UUID clientLineId,
      Reason reason,
      List<Choice> choices,
      String mineToken,
      String instructionToken,
      RecordedMine mine) {

    public static Item of(RecordedConflict conflict, RecordedMine mine) {
      return new Item(
          conflict.slot().key(),
          conflict.slot().lineId(),
          conflict.slot().clientLineId(),
          conflict.reason(),
          conflict.choices(),
          conflict.mineToken(),
          conflict.instructionToken(),
          mine);
    }

    public Slot slot() {
      return new Slot(key, lineId, clientLineId);
    }

    public RecordedConflict toRecorded() {
      return new RecordedConflict(
          slot(), reason, choices == null ? List.of() : choices, mineToken, instructionToken);
    }
  }

  /**
   * The mine a conflict showed where the request's instruction alone does not determine it, because
   * the value was completed from the base the request was made against (CEDIT-02 §5.7): the
   * requested date with its event and place, and a specification with its requirement profile.
   * USE_MINE applies exactly this value; the same instruction resolved against a newer base could
   * mean something else.
   */
  public record RecordedMine(
      RequestedDateValue requestedDate, RecordedSpecification specification) {}

  /**
   * A shown specification: the value with its profile reference, and, when the request carried a
   * requirement-profile input, that input and the profile it resolved to (its fingerprint is the
   * reference's).
   */
  public record RecordedSpecification(
      SpecificationValue value,
      RequirementProfileInput input,
      RequirementProfileSnapshot resolved) {}

  public static OrderEditOperation applied(
      Identity identity, long resultVersion, UUID resultBaseId, List<LineIdMapping> lineIds) {
    return create(identity, Outcome.APPLIED, resultVersion, resultBaseId, lineIds, null);
  }

  public static OrderEditOperation noChange(
      Identity identity, long resultVersion, UUID resultBaseId) {
    return create(identity, Outcome.NO_CHANGE, resultVersion, resultBaseId, List.of(), null);
  }

  public static OrderEditOperation conflict(
      Identity identity, UUID conflictBaseId, ConflictRecord conflict) {
    Objects.requireNonNull(conflict, "A conflict receipt records its conflict");
    return create(identity, Outcome.CONFLICT, null, conflictBaseId, List.of(), conflict);
  }

  /** Who saved what, on which order, against which base, and when. */
  public record Identity(
      UUID operationId,
      UUID salesOrderId,
      UUID actorId,
      UUID baseId,
      String requestFingerprint,
      Instant recordedAt) {
    public Identity {
      Objects.requireNonNull(operationId, "Operation id is required");
      Objects.requireNonNull(salesOrderId, "Order is required");
      Objects.requireNonNull(actorId, "Actor is required");
      Objects.requireNonNull(baseId, "Base is required");
      Objects.requireNonNull(requestFingerprint, "Request fingerprint is required");
      Objects.requireNonNull(recordedAt, "Recording time is required");
    }
  }

  private static OrderEditOperation create(
      Identity identity,
      Outcome outcome,
      Long resultVersion,
      UUID resultBaseId,
      List<LineIdMapping> lineIds,
      ConflictRecord conflict) {
    Objects.requireNonNull(resultBaseId, "A receipt names its resulting base");
    OrderEditOperation receipt = new OrderEditOperation();
    receipt.operationId = identity.operationId();
    receipt.salesOrderId = identity.salesOrderId();
    receipt.actorId = identity.actorId();
    receipt.baseId = identity.baseId();
    receipt.requestFingerprint = identity.requestFingerprint();
    receipt.recordedAt = identity.recordedAt();
    receipt.outcome = outcome;
    receipt.resultVersion = resultVersion;
    receipt.resultBaseId = resultBaseId;
    receipt.lineIds = List.copyOf(lineIds);
    receipt.conflict = conflict;
    return receipt;
  }

  /** The same save repeated: same order, same actor, same content. */
  public boolean isRepeatOf(UUID orderId, UUID actor, String fingerprint) {
    return salesOrderId.equals(orderId)
        && actorId.equals(actor)
        && requestFingerprint.equals(fingerprint);
  }

  /** The recorded conflicts with what they showed, as a later resolution substitutes it. */
  public List<Item> conflictItems() {
    return conflict == null ? List.of() : conflict.items();
  }

  @Override
  protected String getModuleCode() {
    return "OEO";
  }
}
