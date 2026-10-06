package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.FieldInstruction;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.LineOperation;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.Parsed;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Add;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.LineInstruction;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Remove;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Update;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation;
import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation.LineIdMapping;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.AgreementValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ContactValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.DeliveryTermsValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.PricingValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ProfileRef;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.QuantityValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.RequestedDateValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ToleranceValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.WidthValue;
import com.fabricmanagement.sales.salesorder.domain.RequestedDate;
import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderAgreementContextValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderContactValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDeliveryTermsValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineEditOperation;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLinePricingValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineQuantityValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineSpecificationValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineToleranceValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineWidthValue;
import com.fabricmanagement.sales.salesorder.infra.repository.RequirementProfileVersionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Named appliers of the safe-edit keys (CEDIT-02 §5.3): turns each instruction into the value it
 * asks for against the base ("mine"), and applies a merged result to the locked order and lines
 * through the domain's own methods and rules. Nothing here reaches an entity through a free path,
 * reflection or a map. A domain rule broken by the merged result is reported with the key and line
 * it was found on; the save's transaction then rolls back as a whole.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderEditApplier {

  private final RequirementProfileResolver profileResolver;
  private final RequirementProfileService profiles;
  private final RequirementProfileVersionRepository profileVersions;
  private final ModuleSpecsValidator moduleSpecsValidator;
  private final LineAllocationPolicy lineAllocations;
  private final OrderIntakeHooks orderIntakeHooks;
  private final DeliveryCommitmentService deliveryCommitments;
  private final SalesOrderLineRepository lineRepository;

  /** A requirement-profile input resolved against the base, with what it resolved to. */
  public record ProfileResolution(
      RequirementProfileInput input,
      Map<String, Object> residualModuleSpecs,
      RequirementProfileSnapshot resolved) {}

  /**
   * What a request asks, as values against its base: the merge's instructions, and the profile
   * resolutions an applied specification must reproduce.
   */
  public record Mine(
      OrderEditMerge.Instructions instructions,
      Map<UUID, ProfileResolution> lineProfiles,
      Map<UUID, ProfileResolution> addedLineProfiles) {

    /** Resolved profiles by fingerprint, to show what a specification conflict asked for. */
    public Map<String, RequirementProfileSnapshot> resolvedByFingerprint() {
      Map<String, RequirementProfileSnapshot> byFingerprint = new HashMap<>();
      lineProfiles
          .values()
          .forEach(r -> byFingerprint.put(r.resolved().fingerprint(), r.resolved()));
      addedLineProfiles
          .values()
          .forEach(r -> byFingerprint.put(r.resolved().fingerprint(), r.resolved()));
      return byFingerprint;
    }
  }

  /** What an applied save changed. */
  public record Applied(
      List<SalesOrderLine> activeLines,
      List<LineIdMapping> addedLines,
      Map<UUID, SalesOrderLine> addedByClientId) {}

  /**
   * Turns the request's instructions into values against {@code base} (§5.1 "mine"). A line the
   * base does not hold is refused without telling whether it exists. A requirement-profile input is
   * resolved against the base line's pinned profile, without writing anything.
   */
  public Mine mine(Parsed parsed, OrderEditSnapshot base) {
    return mine(parsed, base, Map.of());
  }

  /**
   * As {@link #mine(Parsed, OrderEditSnapshot)}, with the slots a USE_MINE resolution chose by
   * sending the recorded instruction again taken from the receipt as shown (CEDIT-02 §5.7): their
   * instruction is not resolved against this base at all. Resolving it would be thrown away, and
   * against a newer base it may not even be valid (a partial profile's deviation whose facet
   * another user changed). Every other slot is resolved as usual; the line must still be in the
   * base.
   */
  public Mine mine(
      Parsed parsed, OrderEditSnapshot base, Map<Slot, OrderEditOperation.RecordedMine> chosen) {
    Map<OrderEditKey, Object> header = new LinkedHashMap<>();
    parsed
        .header()
        .forEach(
            (key, instruction) -> {
              OrderEditOperation.RecordedMine recorded = chosen.get(Slot.header(key));
              header.put(
                  key,
                  recorded != null && recorded.requestedDate() != null
                      ? recorded.requestedDate()
                      : headerMine(instruction, base.header()));
            });

    List<LineInstruction> lines = new ArrayList<>();
    Map<UUID, ProfileResolution> lineProfiles = new HashMap<>();
    Map<UUID, ProfileResolution> addedProfiles = new HashMap<>();
    for (LineOperation operation : parsed.lines()) {
      switch (operation.operation()) {
        case REMOVE -> {
          requireInBase(base, operation.lineId());
          lines.add(new Remove(operation.lineId()));
        }
        case UPDATE -> {
          OrderEditSnapshot.Line baseLine = requireInBase(base, operation.lineId());
          Map<OrderEditKey, Object> fields = new LinkedHashMap<>();
          for (FieldInstruction instruction : operation.fields().values()) {
            Object value;
            OrderEditOperation.RecordedSpecification recorded =
                instruction.key() == OrderEditKey.LINE_SPECIFICATION
                    ? chosenSpecification(chosen, operation.lineId())
                    : null;
            if (recorded != null) {
              value = recorded.value();
              if (recorded.resolved() != null) {
                lineProfiles.put(
                    operation.lineId(),
                    new ProfileResolution(
                        recorded.input(), recorded.value().moduleSpecs(), recorded.resolved()));
              }
            } else if (instruction.key() == OrderEditKey.LINE_SPECIFICATION) {
              value =
                  specificationMine(
                      instruction,
                      baseLine.productId(),
                      baseLine.specification(),
                      resolution -> lineProfiles.put(operation.lineId(), resolution),
                      operation.lineId(),
                      null);
            } else {
              value = lineMine(instruction);
            }
            fields.put(instruction.key(), value);
          }
          lines.add(new Update(operation.lineId(), fields));
        }
        case ADD -> {
          Map<OrderEditKey, Object> fields = new LinkedHashMap<>();
          for (FieldInstruction instruction : operation.fields().values()) {
            Object value;
            if (instruction.key() == OrderEditKey.LINE_SPECIFICATION) {
              value =
                  specificationMine(
                      instruction,
                      operation.productId(),
                      null,
                      resolution -> addedProfiles.put(operation.clientLineId(), resolution),
                      null,
                      operation.clientLineId());
            } else {
              value = lineMine(instruction);
            }
            fields.put(instruction.key(), value);
          }
          lines.add(new Add(operation.clientLineId(), operation.productId(), fields));
        }
      }
    }
    return new Mine(
        new OrderEditMerge.Instructions(
            header,
            lines,
            parsed.resolutions().keySet(),
            SalesOrderEditInstructions.instructionTokens(parsed)),
        lineProfiles,
        addedProfiles);
  }

  /**
   * The mine a conflict on {@code slot} shows that the instruction alone does not determine, to be
   * kept on the receipt (CEDIT-02 §5.7): the requested date completed with the base's event and
   * place, and a line's specification with its profile resolved against the base line. Null for
   * every other slot: its mine follows from the instruction alone.
   */
  public OrderEditOperation.RecordedMine recordedMine(Slot slot, Mine mine) {
    OrderEditMerge.Instructions instructions = mine.instructions();
    if (slot.lineId() == null && slot.clientLineId() == null) {
      return OrderEditKey.REQUESTED_DELIVERY_DATE.wireName().equals(slot.key())
              && instructions.header().get(OrderEditKey.REQUESTED_DELIVERY_DATE)
                  instanceof RequestedDateValue requested
          ? new OrderEditOperation.RecordedMine(requested, null)
          : null;
    }
    boolean specificationSlot =
        slot.lineId() != null
            && (slot.isWholeLine()
                || OrderEditKey.LINE_SPECIFICATION.wireName().equals(slot.key()));
    if (!specificationSlot) {
      return null;
    }
    for (LineInstruction instruction : instructions.lines()) {
      if (instruction instanceof Update update
          && update.lineId().equals(slot.lineId())
          && update.mine().get(OrderEditKey.LINE_SPECIFICATION)
              instanceof SpecificationValue specification) {
        ProfileResolution profile = mine.lineProfiles().get(slot.lineId());
        return new OrderEditOperation.RecordedMine(
            null,
            new OrderEditOperation.RecordedSpecification(
                specification,
                profile == null ? null : profile.input(),
                profile == null ? null : profile.resolved()));
      }
    }
    return null;
  }

  /**
   * The request's mine with the values its USE_MINE resolutions chose replaced by the mine the
   * conflict showed, as recorded on its receipt. The merge still compares against the request's
   * base, so a later change by someone else conflicts again.
   */
  public Mine withRecordedMines(Mine mine, Map<Slot, OrderEditOperation.RecordedMine> chosen) {
    if (chosen.isEmpty()) {
      return mine;
    }
    OrderEditMerge.Instructions instructions = mine.instructions();
    Map<OrderEditKey, Object> header = new LinkedHashMap<>(instructions.header());
    Map<UUID, SpecificationValue> specifications = new HashMap<>();
    Map<UUID, ProfileResolution> lineProfiles = new HashMap<>(mine.lineProfiles());
    chosen.forEach(
        (slot, recorded) -> {
          if (recorded.requestedDate() != null
              && header.containsKey(OrderEditKey.REQUESTED_DELIVERY_DATE)) {
            header.put(OrderEditKey.REQUESTED_DELIVERY_DATE, recorded.requestedDate());
          }
          OrderEditOperation.RecordedSpecification specification = recorded.specification();
          if (specification != null && slot.lineId() != null) {
            specifications.put(slot.lineId(), specification.value());
            if (specification.resolved() == null) {
              lineProfiles.remove(slot.lineId());
            } else {
              lineProfiles.put(
                  slot.lineId(),
                  new ProfileResolution(
                      specification.input(),
                      specification.value().moduleSpecs(),
                      specification.resolved()));
            }
          }
        });
    List<LineInstruction> lines = new ArrayList<>();
    for (LineInstruction instruction : instructions.lines()) {
      if (instruction instanceof Update update
          && specifications.containsKey(update.lineId())
          && update.mine().containsKey(OrderEditKey.LINE_SPECIFICATION)) {
        Map<OrderEditKey, Object> fields = new LinkedHashMap<>(update.mine());
        fields.put(OrderEditKey.LINE_SPECIFICATION, specifications.get(update.lineId()));
        lines.add(new Update(update.lineId(), fields));
      } else {
        lines.add(instruction);
      }
    }
    return new Mine(
        new OrderEditMerge.Instructions(
            header, lines, instructions.resolved(), instructions.instructionTokens()),
        lineProfiles,
        mine.addedLineProfiles());
  }

  /** The chosen shown specification of a line: on its key slot, or on its whole-line slot. */
  private static OrderEditOperation.RecordedSpecification chosenSpecification(
      Map<Slot, OrderEditOperation.RecordedMine> chosen, UUID lineId) {
    for (Slot slot :
        List.of(Slot.line(OrderEditKey.LINE_SPECIFICATION, lineId), Slot.wholeLine(lineId))) {
      OrderEditOperation.RecordedMine recorded = chosen.get(slot);
      if (recorded != null && recorded.specification() != null) {
        return recorded.specification();
      }
    }
    return null;
  }

  /** A client id adds at most one line to an order, ever; a repeat with a new operation id too. */
  public void assertNewClientLines(UUID orderId, Parsed parsed) {
    UUID tenantId = TenantContext.requireTenantId();
    for (LineOperation operation : parsed.lines()) {
      if (operation.operation() != SalesOrderLineEditOperation.ADD) {
        continue;
      }
      lineRepository
          .findByTenantIdAndSalesOrderIdAndClientLineId(tenantId, orderId, operation.clientLineId())
          .ifPresent(
              existing -> {
                throw SalesOrderEditInstructions.withPlace(
                    OrderDomainException.conflict(
                        "LINE_ALREADY_ADDED", "This new line was already added by an earlier save"),
                    OrderEditKey.LINE,
                    existing.getId(),
                    operation.clientLineId());
              });
    }
  }

  /**
   * Applies a merged result to the locked order and its locked active lines, through the domain's
   * methods: removals, line changes, additions, the catalogue rules over every active line, then
   * the header. The order row is changed last, so it is updated at most once. Does not flush.
   */
  public Applied apply(
      SalesOrder order,
      List<SalesOrderLine> lockedLines,
      OrderEditMerge.Result result,
      Mine mine,
      UUID actor,
      Instant now) {
    UUID tenantId = TenantContext.requireTenantId();
    Map<UUID, SalesOrderLine> byId = new LinkedHashMap<>();
    lockedLines.forEach(line -> byId.put(line.getId(), line));

    // Removals: soft delete with their delivery allocations (ADR-0014 D8).
    List<UUID> removed = new ArrayList<>();
    for (UUID lineId : result.removals()) {
      SalesOrderLine line = byId.remove(lineId);
      if (line != null) {
        line.delete();
        removed.add(lineId);
      }
    }
    lineAllocations.linesRemoved(order.getId(), removed);

    result
        .lineChanges()
        .forEach(
            (lineId, changes) ->
                applyLine(byId.get(lineId), changes, mine.lineProfiles().get(lineId), actor, now));

    List<LineIdMapping> added = new ArrayList<>();
    Map<UUID, SalesOrderLine> addedByClientId = new LinkedHashMap<>();
    for (Add add : result.additions()) {
      SalesOrderLine line =
          addLine(order, add, mine.addedLineProfiles().get(add.clientLineId()), actor, now);
      byId.put(line.getId(), line);
      added.add(new LineIdMapping(add.clientLineId(), line.getId()));
      addedByClientId.put(add.clientLineId(), line);
    }

    List<SalesOrderLine> active = new ArrayList<>(byId.values());
    // The whole merged draft is checked, not only what this save sent (CE-12): two saves that are
    // each valid can together break a rule, such as one distribution twice.
    orderIntakeHooks.validateLines(tenantId, order.getTradingPartnerId(), active);

    // Every check that may query runs before the order row changes: a query's auto-flush after a
    // header change would update the row early and move its version twice.
    DeliveryTerms terms = null;
    if (result.headerChanges().containsKey(OrderEditKey.DELIVERY_TERMS)) {
      terms =
          deliveryTermsFor(
              order, (DeliveryTermsValue) result.headerChanges().get(OrderEditKey.DELIVERY_TERMS));
    }
    DeliveryTerms checkedTerms = terms;
    result.headerChanges().forEach((key, value) -> applyHeader(order, key, value, checkedTerms));
    if (!result.removals().isEmpty()
        || !result.additions().isEmpty()
        || result.lineChanges().values().stream()
            .anyMatch(changes -> changes.containsKey(OrderEditKey.LINE_SPECIFICATION))) {
      order.setModuleType(SalesOrderService.deriveOrderModuleType(active));
    }
    return new Applied(List.copyOf(active), List.copyOf(added), addedByClientId);
  }

  // ── mine ──────────────────────────────────────────────────────────────────

  private static OrderEditSnapshot.Line requireInBase(OrderEditSnapshot base, UUID lineId) {
    return base.line(lineId)
        .orElseThrow(
            () ->
                SalesOrderEditInstructions.invalid(
                    "LINE_NOT_IN_BASE",
                    "The line is not part of the base this save was made against",
                    OrderEditKey.LINE,
                    lineId,
                    null));
  }

  private static Object headerMine(FieldInstruction instruction, OrderEditSnapshot.Header base) {
    boolean clear = instruction.clear();
    Object value = instruction.value();
    return switch (instruction.key()) {
      case CUSTOMER_REFERENCE,
          PAYMENT_TERMS,
          SHIPPING_ADDRESS,
          BILLING_ADDRESS,
          SHIPPING_METHOD,
          NOTES ->
          clear ? null : (String) value;
      case ORDER_DATE, DEADLINE -> clear ? null : (LocalDate) value;
      case REQUESTED_DELIVERY_DATE ->
          base.requestedDate().withLegacyDate(clear ? null : (LocalDate) value);
      case DELIVERY_TERMS ->
          clear ? DeliveryTermsValue.NONE : deliveryTerms((SalesOrderDeliveryTermsValue) value);
      case AGREEMENT_CONTEXT ->
          clear ? AgreementValue.NONE : agreement((SalesOrderAgreementContextValue) value);
      case CONTACT -> clear ? ContactValue.NONE : contact((SalesOrderContactValue) value);
      default -> throw new IllegalArgumentException(instruction.key() + " is not a header key");
    };
  }

  private static Object lineMine(FieldInstruction instruction) {
    boolean clear = instruction.clear();
    Object value = instruction.value();
    return switch (instruction.key()) {
      case LINE_PRODUCT_DESC -> clear ? null : (String) value;
      case LINE_COLOR -> clear ? null : (UUID) value;
      case LINE_REQUESTED_DELIVERY_DATE -> clear ? null : (LocalDate) value;
      case LINE_SINGLE_LOT_REQUIRED -> Boolean.TRUE.equals(value);
      case LINE_FINISHED_WIDTH -> {
        if (clear) {
          yield WidthValue.NONE;
        }
        SalesOrderLineWidthValue width = (SalesOrderLineWidthValue) value;
        yield new WidthValue(width.value(), SalesOrderService.normaliseWidthUnit(width.unit()));
      }
      case LINE_QUANTITY -> {
        SalesOrderLineQuantityValue quantity = (SalesOrderLineQuantityValue) value;
        yield new QuantityValue(quantity.requestedQty(), quantity.unit());
      }
      case LINE_PRICING -> {
        if (clear) {
          yield PricingValue.NONE;
        }
        SalesOrderLinePricingValue pricing = (SalesOrderLinePricingValue) value;
        yield new PricingValue(
            pricing.currency(), pricing.unitPrice(), pricing.discountAmount(), pricing.taxAmount());
      }
      case LINE_TOLERANCE -> {
        if (clear) {
          yield ToleranceValue.NONE;
        }
        SalesOrderLineToleranceValue tolerance = (SalesOrderLineToleranceValue) value;
        yield new ToleranceValue(tolerance.upPct(), tolerance.downPct());
      }
      default -> throw new IllegalArgumentException(instruction.key() + " is not a plain line key");
    };
  }

  /**
   * The specification a request asks for. Without a profile input the base's profile is kept; with
   * one, the input is resolved against the base line's pinned profile (§2.5): partial input keeps
   * the base facets, a new basis resolves from scratch. Nothing is written.
   */
  private SpecificationValue specificationMine(
      FieldInstruction instruction,
      UUID productId,
      SpecificationValue base,
      java.util.function.Consumer<ProfileResolution> resolved,
      UUID lineId,
      UUID clientLineId) {
    SalesOrderLineSpecificationValue value = (SalesOrderLineSpecificationValue) instruction.value();
    RequirementProfileInput input = value.requirementProfile();
    if (input == null) {
      return new SpecificationValue(
          value.moduleType(), value.moduleSpecs(), base == null ? null : base.requirementProfile());
    }
    ProfileRef pinned = base == null ? null : base.requirementProfile();
    RequirementProfileSnapshot baseProfile = pinned == null ? null : pinnedProfile(pinned);
    RequirementProfileSnapshot candidate;
    try {
      candidate =
          profileResolver.resolve(
              new RequirementProfileResolver.LineContext(
                  productId,
                  value.moduleType(),
                  baseProfile,
                  pinned == null ? null : pinned.profileId(),
                  pinned == null ? null : pinned.profileVersion()),
              input,
              value.moduleSpecs());
    } catch (DomainException failure) {
      throw SalesOrderEditInstructions.withPlace(
          failure, OrderEditKey.LINE_SPECIFICATION.wireName(), lineId, clientLineId);
    } catch (IllegalArgumentException failure) {
      throw SalesOrderEditInstructions.withPlace(
          new OrderDomainException(failure.getMessage(), failure),
          OrderEditKey.LINE_SPECIFICATION.wireName(),
          lineId,
          clientLineId);
    }
    resolved.accept(new ProfileResolution(input, value.moduleSpecs(), candidate));
    return new SpecificationValue(
        value.moduleType(),
        value.moduleSpecs(),
        new ProfileRef(null, null, candidate.fingerprint()));
  }

  /** The base's profile, read by its pinned identity and version; versions are append-only. */
  private RequirementProfileSnapshot pinnedProfile(ProfileRef pinned) {
    return profileVersions
        .findByTenantIdAndProfileIdAndProfileVersion(
            TenantContext.requireTenantId(), pinned.profileId(), pinned.profileVersion())
        .map(RequirementProfileVersion::getSnapshot)
        .orElseThrow(
            () ->
                OrderDomainException.internal(
                    "EDIT_BASE_PROFILE_MISSING",
                    "The requirement profile pinned by the edit base is missing"));
  }

  private static DeliveryTermsValue deliveryTerms(SalesOrderDeliveryTermsValue value) {
    // The domain's own normalisation (DeliveryTerms.of, the status default), without its checks:
    // those run on the merged result, after conflicts are known.
    if (value.term() == null) {
      return new DeliveryTermsValue(
          null,
          trimmed(value.place()),
          value.incotermsVersion(),
          value.status(),
          trimmed(value.contractReference()));
    }
    return new DeliveryTermsValue(
        value.term(),
        trimmed(value.place()),
        value.incotermsVersion() == null ? IncotermsVersion.CURRENT : value.incotermsVersion(),
        value.status() == null ? DeliveryTermStatus.PROPOSED : value.status(),
        trimmed(value.contractReference()));
  }

  private static AgreementValue agreement(SalesOrderAgreementContextValue value) {
    return new AgreementValue(value.context(), trimmed(value.note()));
  }

  private static ContactValue contact(SalesOrderContactValue value) {
    String phone = SalesOrderService.blankToNull(value.phone());
    return new ContactValue(
        SalesOrderService.blankToNull(value.name()),
        SalesOrderService.blankToNull(value.email()),
        phone,
        Boolean.TRUE.equals(value.whatsapp()) && phone != null);
  }

  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  // ── apply ─────────────────────────────────────────────────────────────────

  private void applyLine(
      SalesOrderLine line,
      Map<OrderEditKey, Object> changes,
      ProfileResolution profile,
      UUID actor,
      Instant now) {
    // Quantity first: the price is checked against the quantity it will have.
    if (changes.containsKey(OrderEditKey.LINE_QUANTITY)) {
      QuantityValue quantity = (QuantityValue) changes.get(OrderEditKey.LINE_QUANTITY);
      onLine(
          line,
          OrderEditKey.LINE_QUANTITY,
          () -> {
            lineAllocations.assertChange(line, quantity.requestedQty(), quantity.unit());
            line.setRequestedQty(quantity.requestedQty());
            line.setUnit(quantity.unit());
            if (!changes.containsKey(OrderEditKey.LINE_PRICING)) {
              line.assertAdjustmentsFitQuantity();
            }
          });
    }
    changes.forEach(
        (key, value) -> {
          switch (key) {
            case LINE_QUANTITY, LINE_SPECIFICATION -> {
              // Applied before and after the others.
            }
            case LINE_PRICING ->
                onLine(
                    line,
                    key,
                    () -> {
                      PricingValue pricing = (PricingValue) value;
                      line.updatePricing(
                          pricing.currency(),
                          pricing.unitPrice(),
                          pricing.discountAmount(),
                          pricing.taxAmount());
                    });
            case LINE_TOLERANCE ->
                onLine(
                    line,
                    key,
                    () -> {
                      ToleranceValue tolerance = (ToleranceValue) value;
                      line.recordTolerance(tolerance.upPct(), tolerance.downPct(), actor, now);
                    });
            case LINE_PRODUCT_DESC -> line.setProductDesc((String) value);
            case LINE_COLOR -> line.setColorId((UUID) value);
            case LINE_FINISHED_WIDTH -> {
              WidthValue width = (WidthValue) value;
              line.setFinishedWidth(width.value());
              line.setFinishedWidthUnit(width.unit());
            }
            case LINE_REQUESTED_DELIVERY_DATE -> line.setRequestedDeliveryDate((LocalDate) value);
            case LINE_SINGLE_LOT_REQUIRED -> line.setSingleLotRequired((Boolean) value);
            default -> throw new IllegalArgumentException(key + " is not a line key");
          }
        });
    if (changes.containsKey(OrderEditKey.LINE_SPECIFICATION)) {
      SpecificationValue specification =
          (SpecificationValue) changes.get(OrderEditKey.LINE_SPECIFICATION);
      onLine(
          line,
          OrderEditKey.LINE_SPECIFICATION,
          () -> applySpecification(line, specification, profile));
    }
  }

  /**
   * Applies a specification to a line that already exists in the database. The profile saved is the
   * one the save's mine names (CEDIT-02 §2.5: the specification is one key): the snapshot the input
   * resolved to for the save, or the pinned version the mine keeps. It is stored as the line's next
   * version; the line must then carry the mine's fingerprint, or the transaction is rolled back as
   * an internal inconsistency.
   */
  private void applySpecification(
      SalesOrderLine line, SpecificationValue specification, ProfileResolution profile) {
    RequirementProfileInput input = profile == null ? null : profile.input();
    SalesOrderService.assertProfileContextChange(
        line, line.getProductId(), specification.moduleType(), input);
    moduleSpecsValidator.validate(
        specification.moduleType(),
        specification.moduleSpecs(),
        input,
        line.getRequirementProfileSnapshot());
    RequirementProfileSnapshot target = targetProfile(line, specification, profile);
    line.setModuleType(specification.moduleType());
    line.setModuleSpecs(specification.moduleSpecs());
    if (target != null) {
      assertProfileApplied(
          profiles.applyResolved(line, target), specification.profileFingerprint(), "line");
    }
  }

  /** The profile a specification asks for when the line does not already carry it, or null. */
  private RequirementProfileSnapshot targetProfile(
      SalesOrderLine line, SpecificationValue specification, ProfileResolution profile) {
    if (profile != null) {
      return profile.resolved();
    }
    ProfileRef reference = specification.requirementProfile();
    if (reference == null) {
      if (line.getRequirementProfileFingerprint() != null) {
        // Profile versions are append-only; a line never goes back to having none.
        throw OrderDomainException.invalid(
            "REQUIREMENT_PROFILE_CANNOT_BE_REMOVED",
            "This line now has a requirement profile, which cannot be removed; keep the current"
                + " specification or enter a new one");
      }
      return null;
    }
    if (reference.fingerprint().equals(line.getRequirementProfileFingerprint())) {
      return null;
    }
    return pinnedProfile(reference);
  }

  private static void assertProfileApplied(
      RequirementProfileSnapshot applied, String expectedFingerprint, String where) {
    if (applied == null || !applied.fingerprint().equals(expectedFingerprint)) {
      throw OrderDomainException.internal(
          "EDIT_PROFILE_INCONSISTENT",
          "The requirement profile applied to the "
              + where
              + " differs from the one resolved for the save");
    }
  }

  private SalesOrderLine addLine(
      SalesOrder order, Add add, ProfileResolution profile, UUID actor, Instant now) {
    Map<OrderEditKey, Object> fields = add.mine();
    QuantityValue quantity = (QuantityValue) fields.get(OrderEditKey.LINE_QUANTITY);
    PricingValue pricing =
        (PricingValue) fields.getOrDefault(OrderEditKey.LINE_PRICING, PricingValue.NONE);
    ToleranceValue tolerance =
        (ToleranceValue) fields.getOrDefault(OrderEditKey.LINE_TOLERANCE, ToleranceValue.NONE);
    WidthValue width =
        (WidthValue) fields.getOrDefault(OrderEditKey.LINE_FINISHED_WIDTH, WidthValue.NONE);
    SpecificationValue specification =
        (SpecificationValue)
            fields.getOrDefault(OrderEditKey.LINE_SPECIFICATION, SpecificationValue.NONE);
    RequirementProfileInput input = profile == null ? null : profile.input();
    try {
      SalesOrderLine.validatePricing(
          quantity.requestedQty(),
          pricing.currency(),
          pricing.unitPrice(),
          pricing.discountAmount(),
          pricing.taxAmount());
      SalesOrderLine line =
          SalesOrderLine.builder()
              .salesOrderId(order.getId())
              .productId(add.productId())
              .clientLineId(add.clientLineId())
              .productDesc((String) fields.get(OrderEditKey.LINE_PRODUCT_DESC))
              .requestedQty(quantity.requestedQty())
              .unit(quantity.unit())
              .currency(pricing.currency())
              .unitPriceAmount(pricing.unitPrice())
              .discountAmountValue(pricing.discountAmount())
              .taxAmountValue(pricing.taxAmount())
              .lineStatus(SalesOrderLineStatus.PENDING)
              .moduleType(specification.moduleType())
              .moduleSpecs(specification.moduleSpecs())
              .colorId((UUID) fields.get(OrderEditKey.LINE_COLOR))
              .finishedWidth(width.value())
              .finishedWidthUnit(width.unit())
              .requestedDeliveryDate(
                  (LocalDate) fields.get(OrderEditKey.LINE_REQUESTED_DELIVERY_DATE))
              .singleLotRequired(
                  Boolean.TRUE.equals(fields.get(OrderEditKey.LINE_SINGLE_LOT_REQUIRED)))
              .build();
      line.recordTolerance(tolerance.upPct(), tolerance.downPct(), actor, now);
      moduleSpecsValidator.validate(
          specification.moduleType(), specification.moduleSpecs(), input, null);
      SalesOrderLine saved = lineRepository.save(line);
      if (profile != null) {
        assertProfileApplied(
            profiles.applyResolved(saved, profile.resolved()),
            specification.profileFingerprint(),
            "new line");
      }
      return saved;
    } catch (DomainException failure) {
      throw SalesOrderEditInstructions.withPlace(
          failure, OrderEditKey.LINE, null, add.clientLineId());
    }
  }

  /** The delivery terms a save asks for, checked against the order's delivery commitments. */
  private DeliveryTerms deliveryTermsFor(SalesOrder order, DeliveryTermsValue requested) {
    try {
      DeliveryTerms terms =
          DeliveryTerms.of(requested.term(), requested.place(), requested.incotermsVersion());
      // The term of an agreed delivery commitment changes only through a new commitment.
      deliveryCommitments.assertTermsEditable(order, terms);
      return terms;
    } catch (DomainException failure) {
      throw SalesOrderEditInstructions.withPlace(
          failure, OrderEditKey.DELIVERY_TERMS.wireName(), null, null);
    }
  }

  private static void applyHeader(
      SalesOrder order, OrderEditKey key, Object value, DeliveryTerms checkedTerms) {
    try {
      switch (key) {
        case CUSTOMER_REFERENCE -> order.setCustomerReference((String) value);
        case ORDER_DATE -> order.setOrderDate((LocalDate) value);
        case REQUESTED_DELIVERY_DATE -> {
          // The whole value merged, with its event and place: what a conflict showed is saved.
          RequestedDateValue requested = (RequestedDateValue) value;
          // As recorded, without RequestedDate.of's checks: a legacy row the order form never
          // validated (an event without a status) is saved as the legacy update would.
          order.applyRequestedDate(
              new RequestedDate(
                  requested.status(), requested.date(), requested.event(), requested.place()));
        }
        case DELIVERY_TERMS -> {
          DeliveryTermsValue requested = (DeliveryTermsValue) value;
          order.applyDeliveryTerms(checkedTerms);
          // As the legacy update: a status without a term is refused by the domain, not dropped.
          order.applyDeliveryTermStatus(requested.status(), requested.contractReference());
        }
        case PAYMENT_TERMS -> order.setPaymentTerms((String) value);
        case AGREEMENT_CONTEXT -> {
          AgreementValue agreement = (AgreementValue) value;
          order.applyAgreementContext(agreement.context(), agreement.note());
        }
        case CONTACT -> {
          ContactValue contact = (ContactValue) value;
          order.setContactName(contact.name());
          order.setContactEmail(contact.email());
          order.setContactPhone(contact.phone());
          order.setContactWhatsapp(contact.whatsapp());
        }
        case SHIPPING_ADDRESS -> order.setShippingAddress((String) value);
        case BILLING_ADDRESS -> order.setBillingAddress((String) value);
        case SHIPPING_METHOD -> order.setShippingMethod((String) value);
        case NOTES -> order.setNotes((String) value);
        case DEADLINE -> order.setDeadline((LocalDate) value);
        default -> throw new IllegalArgumentException(key + " is not a header key");
      }
    } catch (DomainException failure) {
      throw SalesOrderEditInstructions.withPlace(failure, key.wireName(), null, null);
    }
  }

  private static void onLine(SalesOrderLine line, OrderEditKey key, Runnable change) {
    try {
      change.run();
    } catch (DomainException failure) {
      throw SalesOrderEditInstructions.withPlace(failure, key.wireName(), line.getId(), null);
    }
  }
}
