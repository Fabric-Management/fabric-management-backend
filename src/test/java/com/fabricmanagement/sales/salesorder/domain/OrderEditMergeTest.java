package com.fabricmanagement.sales.salesorder.domain;

import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.AGREEMENT_CONTEXT;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.BILLING_ADDRESS;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.CONTACT;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.CUSTOMER_REFERENCE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.DEADLINE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.DELIVERY_TERMS;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_COLOR;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_FINISHED_WIDTH;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_PRICING;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_PRODUCT_DESC;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_QUANTITY;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_REQUESTED_DELIVERY_DATE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_SHIPMENT_PREFERENCE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_SINGLE_LOT_REQUIRED;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_SPECIFICATION;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_TOLERANCE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.NOTES;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.ORDER_DATE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.PAYMENT_TERMS;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.REQUESTED_DELIVERY_DATE;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.SHIPPING_ADDRESS;
import static com.fabricmanagement.sales.salesorder.domain.OrderEditKey.SHIPPING_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Add;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Conflict;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Instructions;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.LineInstruction;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Reason;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.RecordedConflict;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Remove;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Result;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Update;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.AgreementValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ContactValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.DeliveryTermsValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.Header;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.Line;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.PricingValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ProfileRef;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.QuantityValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.RequestedDateValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ToleranceValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.WidthValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The pure three-way merge of the safe edit (CEDIT-02 §5) against the M vectors of
 * CEDIT-02-contract-scenarios.md. Every snapshot is built by hand from the common start: order O at
 * v7 with paymentTerms "30 days", FCA Leeds 2020 PROPOSED, contact Jane Hill, L1 (P1, 1000 M, GBP
 * 4.0000, tolerance 5/5) and L2 (P2, 500 M, GBP 6.5000, 300 M allocated to D1).
 */
class OrderEditMergeTest {

  private static final UUID L1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID L2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID L3 = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
  private static final UUID L5 = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
  private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID P3 = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
  private static final UUID P4 = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
  private static final UUID P5 = UUID.fromString("00000000-0000-0000-0000-0000000000b5");
  private static final UUID P9 = UUID.fromString("00000000-0000-0000-0000-0000000000b9");
  private static final UUID CLIENT_A = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
  private static final UUID CLIENT_B = UUID.fromString("00000000-0000-0000-0000-0000000000cb");
  private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

  private static final String L1_DIGEST = "allocations:none";
  private static final String L2_DIGEST = "allocations:d1=300.000";

  private static final List<Choice> ALL =
      List.of(Choice.KEEP_CURRENT, Choice.USE_MINE, Choice.NEW_VALUE);
  private static final List<Choice> KEEP_OR_MINE = List.of(Choice.KEEP_CURRENT, Choice.USE_MINE);

  // ── §1 independent keys ────────────────────────────────────────────────────

  @Nested
  class IndependentKeys {

    @Test
    @DisplayName("S1.1: notes by A then paymentTerms CLEAR by B, both against B0, both apply")
    void independentKeysApplyInOrder() {
      OrderEditSnapshot b0 = start();

      Result a = merge(b0, b0, header(mine(NOTES, "Urgent")));
      assertThat(a.conflicts()).isEmpty();
      assertThat(a.headerChanges()).containsExactly(entry(NOTES, "Urgent"));

      OrderEditSnapshot v8 = withHeader(b0, NOTES, "Urgent");
      Result b = merge(b0, v8, header(mine(PAYMENT_TERMS, null)));
      assertThat(b.conflicts()).isEmpty();
      assertThat(b.headerChanges()).containsExactly(entry(PAYMENT_TERMS, null));
      assertThat(b.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("S1.2: the reverse order of S1.1 gives the same two changes")
    void independentKeysApplyInReverseOrder() {
      OrderEditSnapshot b0 = start();

      Result b = merge(b0, b0, header(mine(PAYMENT_TERMS, null)));
      assertThat(b.conflicts()).isEmpty();
      assertThat(b.headerChanges()).containsExactly(entry(PAYMENT_TERMS, null));

      OrderEditSnapshot v8 = withHeader(b0, PAYMENT_TERMS, null);
      Result a = merge(b0, v8, header(mine(NOTES, "Urgent")));
      assertThat(a.conflicts()).isEmpty();
      assertThat(a.headerChanges()).containsExactly(entry(NOTES, "Urgent"));
    }
  }

  // ── §2 clear and untouched keys ────────────────────────────────────────────

  @Nested
  class ClearAndUntouched {

    @Test
    @DisplayName("S2.1: paymentTerms CLEAR while unchanged on the server applies null")
    void clearOfUnchangedKeyApplies() {
      OrderEditSnapshot b0 = start();

      Result result = merge(b0, b0, header(mine(PAYMENT_TERMS, null)));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(PAYMENT_TERMS, null));
    }

    @Test
    @DisplayName("S2.2: a key the request does not name keeps the server's value")
    void untouchedKeyIsKept() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, SHIPPING_METHOD, "Courier");

      Result result = merge(b0, current, header(mine(NOTES, "X")));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsOnlyKeys(NOTES);
      assertThat(result.headerChanges()).doesNotContainKey(SHIPPING_METHOD);
    }

    @Test
    @DisplayName("S2.4: CLEAR of a key changed on the server conflicts with base, current, mine")
    void clearOfChangedKeyConflicts() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");

      Result result = merge(b0, current, header(mine(PAYMENT_TERMS, null)));

      assertThat(result.hasConflicts()).isTrue();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.header(PAYMENT_TERMS));
                assertThat(conflict.slot().key()).isEqualTo("paymentTerms");
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(conflict.base()).isEqualTo("30 days");
                assertThat(conflict.current()).isEqualTo("45 days");
                assertThat(conflict.mine()).isNull();
                assertThat(conflict.choices()).isEqualTo(ALL);
                assertThat(conflict.mineToken()).isEqualTo(OrderEditMerge.token(null));
              });
    }
  }

  // ── §3 same key, same value, revert ────────────────────────────────────────

  @Nested
  class SameKey {

    @Test
    @DisplayName("S3.1: a different value saved meanwhile is CHANGED_ON_SERVER with three choices")
    void differentValueConflicts() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");

      Result result = merge(b0, current, header(mine(PAYMENT_TERMS, "60 days")));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(conflict.base()).isEqualTo("30 days");
                assertThat(conflict.current()).isEqualTo("45 days");
                assertThat(conflict.mine()).isEqualTo("60 days");
                assertThat(conflict.choices())
                    .containsExactly(Choice.KEEP_CURRENT, Choice.USE_MINE, Choice.NEW_VALUE);
                assertThat(conflict.mineToken()).isEqualTo(OrderEditMerge.token("60 days"));
              });
    }

    @Test
    @DisplayName("S3.2: the same value saved meanwhile is NO_CHANGE")
    void sameValueSavedMeanwhileIsNoChange() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "60 days");

      Result result = merge(b0, current, header(mine(PAYMENT_TERMS, "60 days")));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S3.3: mine equal to the base keeps the server's newer value (NO_CHANGE)")
    void revertToBaseKeepsCurrent() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");

      Result result = merge(b0, current, header(mine(PAYMENT_TERMS, "30 days")));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S3.4, S15.14: GBP 4.00 against GBP 4.0000 is the same price (NO_CHANGE)")
    void decimalPriceComparesByValue() {
      OrderEditSnapshot b0 = start();

      PricingValue sameValue = new PricingValue("GBP", dec("4.00"), null, null);

      Result result = merge(b0, b0, lines(update(L1, mine(LINE_PRICING, sameValue))));

      assertNoChange(result);
    }
  }

  // ── §4 valid alone, invalid together ───────────────────────────────────────

  @Nested
  class CompositeKeys {

    @Test
    @DisplayName(
        "S4.1: A's discount and B's quantity are independent keys; the merge returns B's quantity"
            + " on top of A's pricing and leaves the combined check to the domain")
    void independentLineKeysBothSurvive() {
      OrderEditSnapshot b0 = start();
      PricingValue discounted = new PricingValue("GBP", dec("4.0000"), dec("3500"), null);
      OrderEditSnapshot current = withLine(b0, L1, LINE_PRICING, discounted);
      QuantityValue eightHundred = qty("800", "M");

      Result result = merge(b0, current, lines(update(L1, mine(LINE_QUANTITY, eightHundred))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges()).containsOnlyKeys(L1);
      assertThat(result.lineChanges().get(L1)).containsExactly(entry(LINE_QUANTITY, eightHundred));
      // The candidate the domain validates is current (A's discount kept) plus B's quantity.
      assertThat(current.line(L1).orElseThrow().pricing()).isEqualTo(discounted);
    }

    @Test
    @DisplayName("S4.2: delivery terms conflict as one key with their composite values")
    void deliveryTermsConflictAsAWhole() {
      OrderEditSnapshot b0 = start();
      DeliveryTermsValue agreed =
          new DeliveryTermsValue(
              DeliveryTerm.FCA,
              "Leeds",
              IncotermsVersion.INCOTERMS_2020,
              DeliveryTermStatus.AGREED_BY_CONTRACT,
              "C-12");
      DeliveryTermsValue york =
          new DeliveryTermsValue(
              DeliveryTerm.FCA,
              "York",
              IncotermsVersion.INCOTERMS_2020,
              DeliveryTermStatus.PROPOSED,
              null);
      OrderEditSnapshot current = withHeader(b0, DELIVERY_TERMS, agreed);

      Result result = merge(b0, current, header(mine(DELIVERY_TERMS, york)));

      assertThat(result.headerChanges()).isEmpty();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot().key()).isEqualTo("deliveryTerms");
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(conflict.base()).isEqualTo(b0.header().deliveryTerms());
                assertThat(conflict.current()).isEqualTo(agreed);
                assertThat(conflict.mine()).isEqualTo(york);
              });
    }

    @Test
    @DisplayName("S4.3: a contact changed on the server conflicts as one key")
    void contactConflictsAsAWhole() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current =
          withHeader(b0, CONTACT, new ContactValue("Jane Hill", "j.hill@example.com", null, false));
      ContactValue mark = new ContactValue("Mark Lee", "mark@example.com", null, false);

      Result result = merge(b0, current, header(mine(CONTACT, mark)));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.header(CONTACT));
                assertThat(conflict.slot().key()).isEqualTo("contact");
                assertThat(conflict.mine()).isEqualTo(mark);
              });
    }
  }

  // ── §5 no partial application ──────────────────────────────────────────────

  @Nested
  class NoPartialApplication {

    @Test
    @DisplayName("S5.1: one conflicting key fails the request; the conflict lists only that key")
    void oneConflictRejectsTheWholeRequest() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");

      Result result = merge(b0, current, header(mine(NOTES, "Urgent", PAYMENT_TERMS, "60 days")));

      assertThat(result.hasConflicts()).isTrue();
      assertThat(result.conflicts())
          .extracting(Conflict::slot)
          .containsExactly(Slot.header(PAYMENT_TERMS));
    }

    @Test
    @DisplayName(
        "S5.2: the conflict is found first even when another key's value would fail validation")
    void conflictIsFoundRegardlessOfAnInvalidValue() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");
      // A discount above the line amount (4.0000 x 1000): the domain refuses it, the merge
      // does not.
      PricingValue invalid = new PricingValue("GBP", dec("4.0000"), dec("99999"), null);

      Result result =
          merge(
              b0,
              current,
              request(
                  mine(NOTES, "Urgent", PAYMENT_TERMS, "60 days"),
                  List.of(update(L1, mine(LINE_PRICING, invalid))),
                  Set.of()));

      assertThat(result.hasConflicts()).isTrue();
      assertThat(result.conflicts())
          .extracting(Conflict::slot, Conflict::reason)
          .containsExactly(tuple(Slot.header(PAYMENT_TERMS), Reason.CHANGED_ON_SERVER));
    }
  }

  // ── §6 lines ───────────────────────────────────────────────────────────────

  @Nested
  class Lines {

    @Test
    @DisplayName("S6.1: two ADDs from the same base both merge, each with its own client id")
    void twoAddsBothMerge() {
      OrderEditSnapshot b0 = start();
      Add addA = new Add(CLIENT_A, P3, mine(LINE_QUANTITY, qty("200", "M")));
      Add addB = new Add(CLIENT_B, P4, mine(LINE_QUANTITY, qty("300", "M")));

      Result a = merge(b0, b0, lines(addA));
      assertThat(a.conflicts()).isEmpty();
      assertThat(a.additions()).containsExactly(addA);

      OrderEditSnapshot afterA = plus(b0, plainLine(L3, P3, qty("200", "M"), null, null, "x"));
      Result b = merge(b0, afterA, lines(addB));
      assertThat(b.conflicts()).isEmpty();
      assertThat(b.additions()).containsExactly(addB);
      assertThat(b.removals()).isEmpty();
    }

    @Test
    @DisplayName("S6.2: different keys on different lines both merge")
    void differentLinesMerge() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current =
          withLine(b0, L1, LINE_PRICING, new PricingValue("GBP", dec("4.10"), null, null));
      ToleranceValue three = new ToleranceValue(dec("3"), dec("3"));

      Result result = merge(b0, current, lines(update(L2, mine(LINE_TOLERANCE, three))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges()).containsOnlyKeys(L2);
      assertThat(result.lineChanges().get(L2)).containsExactly(entry(LINE_TOLERANCE, three));
    }

    @Test
    @DisplayName("S6.3: different keys on the same line both merge")
    void differentKeysOnTheSameLineMerge() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current =
          withLine(b0, L1, LINE_PRICING, new PricingValue("GBP", dec("4.10"), null, null));
      ToleranceValue three = new ToleranceValue(dec("3"), dec("3"));

      Result result = merge(b0, current, lines(update(L1, mine(LINE_TOLERANCE, three))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L1)).containsExactly(entry(LINE_TOLERANCE, three));
    }

    @Test
    @DisplayName("S6.4: a line added after the base and absent from the request is kept")
    void lineAddedMeanwhileIsKept() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = plus(b0, plainLine(L5, P5, qty("50", "M"), null, null, "x"));

      Result result =
          merge(
              b0,
              current,
              request(
                  mine(NOTES, "Urgent"),
                  List.of(update(L1, mine(LINE_PRODUCT_DESC, "Selvedge note"))),
                  Set.of()));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.removals()).isEmpty();
      assertThat(result.lineChanges()).containsOnlyKeys(L1);
    }
  }

  // ── §7 removal and identity ────────────────────────────────────────────────

  @Nested
  class RemovalAndIdentity {

    @Test
    @DisplayName(
        "S7.1: REMOVE of a line changed on the server is LINE_CHANGED_ON_SERVER naming the key")
    void removeOfChangedLineConflicts() {
      OrderEditSnapshot b0 = start();
      PricingValue raised = new PricingValue("GBP", dec("6.80"), null, null);
      OrderEditSnapshot current = withLine(b0, L2, LINE_PRICING, raised);
      Remove remove = new Remove(L2);

      Result result = merge(b0, current, lines(remove));

      assertThat(result.removals()).isEmpty();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.wholeLine(L2));
                assertThat(conflict.slot().key()).isEqualTo("line");
                assertThat(conflict.reason()).isEqualTo(Reason.LINE_CHANGED_ON_SERVER);
                assertThat(conflict.choices()).isEqualTo(KEEP_OR_MINE);
                assertThat(conflict.mine()).isNull();
                assertThat(conflict.mineToken())
                    .isEqualTo(OrderEditMerge.lineInstructionToken(remove));
                assertThat(conflict.base())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsOnlyKeys("line.pricing")
                    .containsEntry("line.pricing", b0.line(L2).orElseThrow().pricing());
                assertThat(conflict.current())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsOnlyKeys("line.pricing")
                    .containsEntry("line.pricing", raised);
              });
    }

    @Test
    @DisplayName("S7.5 (merge part): an allocation change alone blocks a REMOVE")
    void removeOfLineWithChangedAllocationConflicts() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withAllocation(b0, L2, "allocations:d1=250.000");

      Result result = merge(b0, current, lines(new Remove(L2)));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.reason()).isEqualTo(Reason.LINE_CHANGED_ON_SERVER);
                assertThat(conflict.base())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsOnlyKeys("allocationDigest");
              });
    }

    @Test
    @DisplayName("S7.2: an UPDATE of a line removed on the server is LINE_REMOVED_ON_SERVER")
    void updateOfRemovedLineConflictsWithKeepCurrentOnly() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = without(b0, L2);
      Update update =
          update(L2, mine(LINE_PRICING, new PricingValue("GBP", dec("6.80"), null, null)));

      Result result = merge(b0, current, lines(update));

      assertThat(result.lineChanges()).isEmpty();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.wholeLine(L2));
                assertThat(conflict.reason()).isEqualTo(Reason.LINE_REMOVED_ON_SERVER);
                assertThat(conflict.choices()).containsExactly(Choice.KEEP_CURRENT);
                assertThat(conflict.current()).isNull();
                assertThat(conflict.base())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("lineId", L2)
                    .containsEntry("productId", P2);
                assertThat(conflict.mine())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("lineId", L2)
                    .containsKey("line.pricing");
                assertThat(conflict.mineToken())
                    .isEqualTo(OrderEditMerge.lineInstructionToken(update));
              });
    }

    @Test
    @DisplayName("S7.3: REMOVE of a line already removed on the server is NO_CHANGE")
    void removeOfRemovedLineIsNoChange() {
      OrderEditSnapshot b0 = start();

      Result result = merge(b0, without(b0, L2), lines(new Remove(L2)));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S7.7: REMOVE of an unchanged line applies")
    void removeOfUnchangedLineApplies() {
      OrderEditSnapshot b0 = start();

      Result result = merge(b0, b0, lines(new Remove(L1)));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.removals()).containsExactly(L1);
      assertThat(result.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("S7.6, S14.5 (merge part): an UPDATE of a line whose product changed conflicts")
    void updateOfLineWithChangedProductConflicts() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withProduct(b0, L1, P9);

      Result result =
          merge(
              b0,
              current,
              lines(
                  update(
                      L1, mine(LINE_PRICING, new PricingValue("GBP", dec("4.10"), null, null)))));

      assertThat(result.lineChanges()).isEmpty();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.wholeLine(L1));
                assertThat(conflict.reason()).isEqualTo(Reason.LINE_PRODUCT_CHANGED);
                assertThat(conflict.choices()).isEqualTo(ALL);
                assertThat(conflict.base())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("productId", P1);
                assertThat(conflict.current())
                    .asInstanceOf(InstanceOfAssertFactories.MAP)
                    .containsEntry("productId", P9);
              });
    }

    @Test
    @DisplayName(
        "S7.6 (R1): a product change with a profiled specification offers no USE_MINE; the profile"
            + " was resolved for the earlier product")
    void productChangeWithAProfiledSpecificationOffersNoUseMine() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withProduct(b0, L1, P9);
      SpecificationValue profiled =
          spec(specs("gsm", 180), new ProfileRef(null, null, "f".repeat(64)));

      Result result = merge(b0, current, lines(update(L1, mine(LINE_SPECIFICATION, profiled))));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.reason()).isEqualTo(Reason.LINE_PRODUCT_CHANGED);
                assertThat(conflict.choices())
                    .containsExactly(Choice.KEEP_CURRENT, Choice.NEW_VALUE);
              });
    }

    @Test
    @DisplayName(
        "§2.5 (R1): a specification without a profile against one the server added offers no"
            + " USE_MINE; profile versions are append-only")
    void specificationWithoutAProfileAgainstAnAddedOneOffersNoUseMine() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current =
          withLine(
              b0,
              L1,
              LINE_SPECIFICATION,
              spec(specs(), new ProfileRef(UUID.randomUUID(), 1, "a".repeat(64))));

      Result result =
          merge(
              b0,
              current,
              lines(update(L1, mine(LINE_SPECIFICATION, spec(specs("finish", "soft"), null)))));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(conflict.choices())
                    .containsExactly(Choice.KEEP_CURRENT, Choice.NEW_VALUE);
              });
    }
  }

  // ── §9 resolution and intervening change ───────────────────────────────────

  @Nested
  class Resolution {

    /** S3.1 → B1: the conflict base after B's paymentTerms "60 days" met A's "45 days". */
    private final OrderEditSnapshot b0 = start();

    private final OrderEditSnapshot b1 = withHeader(b0, PAYMENT_TERMS, "45 days");
    private final Map<Slot, String> b1Guard = OrderEditMerge.guardBetween(b0, b1);
    private final Slot paymentTerms = Slot.header(PAYMENT_TERMS);

    private List<RecordedConflict> recordedS31() {
      Result conflicted = merge(b0, b1, header(mine(PAYMENT_TERMS, "60 days")));
      return conflicted.conflicts().stream().map(OrderEditMergeTest::recorded).toList();
    }

    @Test
    @DisplayName("S9.1: USE_MINE with the recorded mine resolves and applies")
    void useMineApplies() {
      Instructions instructions =
          request(mine(PAYMENT_TERMS, "60 days"), List.of(), Set.of(paymentTerms));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(), Map.of(paymentTerms, Choice.USE_MINE), instructions))
          .isEmpty();
      Result result = OrderEditMerge.merge(b1, b1, b1Guard, instructions, false);
      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(PAYMENT_TERMS, "60 days"));
    }

    @Test
    @DisplayName("S9.3: no resolution for a recorded conflict is RESOLUTION_MISMATCH")
    void missingResolutionIsAMismatch() {
      Instructions instructions = request(mine(PAYMENT_TERMS, "60 days"), List.of(), Set.of());

      assertThat(OrderEditMerge.resolutionMismatch(recordedS31(), Map.of(), instructions))
          .contains(paymentTerms);
    }

    @Test
    @DisplayName("S9.4: KEEP_CURRENT with an instruction for the key is RESOLUTION_MISMATCH")
    void keepCurrentWithInstructionIsAMismatch() {
      Instructions instructions =
          request(mine(PAYMENT_TERMS, "60 days"), List.of(), Set.of(paymentTerms));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(), Map.of(paymentTerms, Choice.KEEP_CURRENT), instructions))
          .contains(paymentTerms);
    }

    @Test
    @DisplayName("S9.5: USE_MINE with another value is RESOLUTION_MISMATCH; NEW_VALUE is right")
    void useMineWithAnotherValueIsAMismatch() {
      Instructions instructions =
          request(mine(PAYMENT_TERMS, "61 days"), List.of(), Set.of(paymentTerms));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(), Map.of(paymentTerms, Choice.USE_MINE), instructions))
          .contains(paymentTerms);
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(), Map.of(paymentTerms, Choice.NEW_VALUE), instructions))
          .isEmpty();
    }

    @Test
    @DisplayName(
        "S9.1/§5.7 (R1): the same instruction chooses the shown mine; the shown mine merges"
            + " against the conflict base and a third change conflicts again")
    void useMineOfACompletedValueKeepsTheShownMine() {
      Slot date = Slot.header(REQUESTED_DELIVERY_DATE);
      OrderEditSnapshot base =
          withHeader(
              start(),
              REQUESTED_DELIVERY_DATE,
              new RequestedDateValue(
                  RequestedDateStatus.REQUESTED,
                  LocalDate.of(2026, 11, 1),
                  RequestedDeliveryEvent.HANDED_TO_CARRIER,
                  "Leeds"));
      // Another writer moved the date and also changed its event and place.
      OrderEditSnapshot current =
          withHeader(
              base,
              REQUESTED_DELIVERY_DATE,
              new RequestedDateValue(
                  RequestedDateStatus.REQUESTED,
                  LocalDate.of(2026, 11, 8),
                  RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE,
                  "York"));
      LocalDate sent = LocalDate.of(2026, 11, 15);
      // The order form sends only the date; mine is completed from the base it is sent against.
      Map<Slot, String> sameInstruction = Map.of(date, "SET requestedDeliveryDate 2026-11-15");
      RequestedDateValue shown =
          ((RequestedDateValue) base.header().value(REQUESTED_DELIVERY_DATE)).withLegacyDate(sent);
      Result first =
          merge(
              base,
              current,
              new Instructions(
                  Map.of(REQUESTED_DELIVERY_DATE, shown), List.of(), Set.of(), sameInstruction));
      assertThat(first.conflicts())
          .singleElement()
          .satisfies(
              c -> {
                assertThat(c.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(c.mine()).isEqualTo(shown);
                // The shown mine by the key's equality, and the instruction kept beside it.
                assertThat(c.mineToken()).isEqualTo(OrderEditMerge.token(shown));
                assertThat(c.instructionToken()).isEqualTo(sameInstruction.get(date));
              });
      List<RecordedConflict> recorded =
          first.conflicts().stream().map(OrderEditMergeTest::recorded).toList();

      // The same instruction resolved against the conflict base means another value.
      RequestedDateValue resolvedAgain =
          ((RequestedDateValue) current.header().value(REQUESTED_DELIVERY_DATE))
              .withLegacyDate(sent);
      assertThat(resolvedAgain).isNotEqualTo(shown);
      Instructions resend =
          new Instructions(
              Map.of(REQUESTED_DELIVERY_DATE, resolvedAgain),
              List.of(),
              Set.of(date),
              sameInstruction);
      assertThat(OrderEditMerge.resolutionMismatch(recorded, Map.of(date, Choice.USE_MINE), resend))
          .isEmpty();
      // Judged by the key's equality, the value resolved anew is not the shown mine.
      assertThat(
              OrderEditMerge.resolutionMismatch(recorded, Map.of(date, Choice.NEW_VALUE), resend))
          .isEmpty();

      // USE_MINE: the server substitutes the shown mine from the receipt before merging.
      Instructions chosen =
          new Instructions(
              Map.of(REQUESTED_DELIVERY_DATE, shown), List.of(), Set.of(date), sameInstruction);
      Result applied = merge(current, current, chosen);
      assertThat(applied.conflicts()).isEmpty();
      assertThat(applied.headerChanges()).containsExactly(entry(REQUESTED_DELIVERY_DATE, shown));

      // S9.2: a third change after the conflict base conflicts again, still showing that mine.
      OrderEditSnapshot third =
          withHeader(
              current,
              REQUESTED_DELIVERY_DATE,
              new RequestedDateValue(
                  RequestedDateStatus.REQUESTED,
                  LocalDate.of(2026, 11, 20),
                  RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE,
                  "York"));
      assertThat(merge(current, third, chosen).conflicts())
          .singleElement()
          .satisfies(
              c -> {
                assertThat(c.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(c.mine()).isEqualTo(shown);
                assertThat(c.mineToken()).isEqualTo(recorded.getFirst().mineToken());
              });
    }

    @Test
    @DisplayName(
        "§5.7 (R1): a resolution is judged by the key's equality, not by how its JSON is written")
    void resolutionsCompareResolvedValuesByTheKeysEquality() {
      Slot contact = Slot.header(CONTACT);
      OrderEditSnapshot base = start();
      OrderEditSnapshot current =
          withHeader(base, CONTACT, new ContactValue("Casey Ward", null, null, false));
      ContactValue mine = new ContactValue("Morgan Lee", "morgan@example.com", null, false);
      Result first =
          merge(
              base,
              current,
              new Instructions(
                  Map.of(CONTACT, mine),
                  List.of(),
                  Set.of(),
                  Map.of(contact, "SET contact {name: Morgan Lee}")));
      List<RecordedConflict> recorded =
          first.conflicts().stream().map(OrderEditMergeTest::recorded).toList();
      // Written differently (spaces the domain trims), the same value once resolved.
      Instructions sameValue =
          new Instructions(
              Map.of(CONTACT, mine),
              List.of(),
              Set.of(contact),
              Map.of(contact, "SET contact {name:  Morgan Lee }"));
      Instructions otherValue =
          new Instructions(
              Map.of(CONTACT, new ContactValue("Morgan Lee", null, null, false)),
              List.of(),
              Set.of(contact),
              Map.of(contact, "SET contact {name: Morgan Lee, email: none}"));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(contact, Choice.USE_MINE), sameValue))
          .isEmpty();
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(contact, Choice.NEW_VALUE), sameValue))
          .contains(contact);
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(contact, Choice.USE_MINE), otherValue))
          .contains(contact);
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(contact, Choice.NEW_VALUE), otherValue))
          .isEmpty();
    }

    @Test
    @DisplayName("S9.3: a resolution for a slot without a recorded conflict is a mismatch")
    void resolutionWithoutConflictIsAMismatch() {
      Slot notes = Slot.header(NOTES);
      Instructions instructions =
          request(mine(PAYMENT_TERMS, "60 days"), List.of(), Set.of(paymentTerms, notes));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(),
                  Map.of(paymentTerms, Choice.USE_MINE, notes, Choice.KEEP_CURRENT),
                  instructions))
          .contains(notes);
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  List.of(), Map.of(notes, Choice.KEEP_CURRENT), header(Map.of())))
          .contains(notes);
    }

    @Test
    @DisplayName("S7.2, S9.3: a choice the conflict did not offer is a mismatch")
    void choiceNotOfferedIsAMismatch() {
      Update update =
          update(L2, mine(LINE_PRICING, new PricingValue("GBP", dec("6.80"), null, null)));
      List<RecordedConflict> recorded =
          merge(b0, without(b0, L2), lines(update)).conflicts().stream()
              .map(OrderEditMergeTest::recorded)
              .toList();
      Slot line = Slot.wholeLine(L2);

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(line, Choice.USE_MINE), lines(update)))
          .contains(line);
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(line, Choice.KEEP_CURRENT), header(Map.of())))
          .isEmpty();
    }

    @Test
    @DisplayName("S7.1, S9.1: USE_MINE of a whole-line REMOVE matches the recorded instruction")
    void useMineOfWholeLineMatchesTheSameInstruction() {
      Remove remove = new Remove(L2);
      OrderEditSnapshot current =
          withLine(b0, L2, LINE_PRICING, new PricingValue("GBP", dec("6.80"), null, null));
      List<RecordedConflict> recorded =
          merge(b0, current, lines(remove)).conflicts().stream()
              .map(OrderEditMergeTest::recorded)
              .toList();
      Slot line = Slot.wholeLine(L2);

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(line, Choice.USE_MINE), lines(new Remove(L2))))
          .isEmpty();
      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(line, Choice.KEEP_CURRENT), lines(new Remove(L2))))
          .contains(line);
    }

    @Test
    @DisplayName(
        "S9.6: CLEAR returning the parent's value replaced meanwhile is UNCONFIRMED_REVERT")
    void staleRevertIsUnconfirmed() {
      OrderEditSnapshot conflictBase = withHeader(b0, NOTES, "Urgent");
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, conflictBase);

      Result result =
          OrderEditMerge.merge(conflictBase, conflictBase, guard, header(mine(NOTES, null)), false);

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.header(NOTES));
                assertThat(conflict.reason()).isEqualTo(Reason.UNCONFIRMED_REVERT);
                assertThat(conflict.base()).isEqualTo("Urgent");
                assertThat(conflict.current()).isEqualTo("Urgent");
                assertThat(conflict.mine()).isNull();
                assertThat(conflict.choices()).isEqualTo(ALL);
              });
    }

    @Test
    @DisplayName("S9.7: the confirmed revert (USE_MINE on notes) applies")
    void confirmedRevertApplies() {
      OrderEditSnapshot conflictBase = withHeader(b0, NOTES, "Urgent");
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, conflictBase);
      Slot notes = Slot.header(NOTES);
      List<RecordedConflict> recorded =
          OrderEditMerge.merge(conflictBase, conflictBase, guard, header(mine(NOTES, null)), false)
              .conflicts()
              .stream()
              .map(OrderEditMergeTest::recorded)
              .toList();
      Instructions confirmed = request(mine(NOTES, null), List.of(), Set.of(notes));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(notes, Choice.USE_MINE), confirmed))
          .isEmpty();
      Result result = OrderEditMerge.merge(conflictBase, conflictBase, guard, confirmed, false);
      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(NOTES, null));
    }

    @Test
    @DisplayName("S9.8: a value the actor saved himself is not guarded; CLEAR applies")
    void ownSavedValueIsNotGuarded() {
      // B's own save: base B0, nobody else changed anything, so the next base has no guard.
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, b0);
      assertThat(guard).isEmpty();
      OrderEditSnapshot nextBase = withHeader(b0, NOTES, "Urgent");

      Result result =
          OrderEditMerge.merge(nextBase, nextBase, guard, header(mine(NOTES, null)), false);

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(NOTES, null));
    }

    @Test
    @DisplayName("S9.9: only a KEEP_CURRENT resolution is NO_CHANGE and keeps the server value")
    void keepCurrentAloneIsNoChange() {
      Instructions instructions = request(Map.of(), List.of(), Set.of(paymentTerms));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recordedS31(), Map.of(paymentTerms, Choice.KEEP_CURRENT), instructions))
          .isEmpty();
      Result result = OrderEditMerge.merge(b1, b1, b1Guard, instructions, false);
      assertNoChange(result);
      assertThat(b1.header().paymentTerms()).isEqualTo("45 days");
    }
  }

  // ── §11 expired base (merge part) ──────────────────────────────────────────

  @Nested
  class ExpiredBase {

    @Test
    @DisplayName("S11.3 (merge part): an expired base lists every applying change for review")
    void expiredBaseListsChangesForReview() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, NOTES, "Note from Avery");

      Result result =
          OrderEditMerge.merge(b0, current, Map.of(), header(mine(PAYMENT_TERMS, "60 days")), true);

      assertThat(result.hasConflicts()).isTrue();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.header(PAYMENT_TERMS));
                assertThat(conflict.reason()).isEqualTo(Reason.REVIEW_REQUIRED);
                assertThat(conflict.base()).isEqualTo("30 days");
                assertThat(conflict.current()).isEqualTo("30 days");
                assertThat(conflict.mine()).isEqualTo("60 days");
                assertThat(conflict.choices()).isEqualTo(ALL);
              });
    }

    @Test
    @DisplayName(
        "S11.3 (merge part): an expired base keeps real conflicts once and reviews line"
            + " operations; a live base lists only the conflict")
    void expiredBaseListsConflictsOnceAndReviewsLineOperations() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current = withHeader(b0, PAYMENT_TERMS, "45 days");
      Add add = new Add(CLIENT_A, P3, mine(LINE_QUANTITY, qty("200", "M")));
      Instructions instructions =
          request(
              mine(PAYMENT_TERMS, "60 days", NOTES, "Urgent"),
              List.of(new Remove(L1), add),
              Set.of());

      Result expired = OrderEditMerge.merge(b0, current, Map.of(), instructions, true);
      Result live = OrderEditMerge.merge(b0, current, Map.of(), instructions, false);

      assertThat(expired.conflicts())
          .extracting(Conflict::slot, Conflict::reason, Conflict::choices)
          .containsExactlyInAnyOrder(
              tuple(Slot.header(PAYMENT_TERMS), Reason.CHANGED_ON_SERVER, ALL),
              tuple(Slot.header(NOTES), Reason.REVIEW_REQUIRED, ALL),
              tuple(Slot.wholeLine(L1), Reason.REVIEW_REQUIRED, KEEP_OR_MINE),
              tuple(Slot.addedLine(CLIENT_A), Reason.REVIEW_REQUIRED, ALL));
      assertThat(live.conflicts())
          .extracting(Conflict::slot)
          .containsExactly(Slot.header(PAYMENT_TERMS));
    }

    @Test
    @DisplayName("S11.3 (merge part): an expired base with nothing to apply lists nothing")
    void expiredBaseWithoutChangeListsNothing() {
      OrderEditSnapshot b0 = start();

      Result result =
          OrderEditMerge.merge(b0, b0, Map.of(), header(mine(PAYMENT_TERMS, "30 days")), true);

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.hasChanges()).isFalse();
    }
  }

  // ── §12 decimals, false/zero/null, specification ───────────────────────────

  @Nested
  class Equality {

    @Test
    @DisplayName("S12.1: 1000.000 M is the same quantity as 1000 M (NO_CHANGE)")
    void quantityComparesByValue() {
      OrderEditSnapshot b0 = start();

      Result result = merge(b0, b0, lines(update(L1, mine(LINE_QUANTITY, qty("1000.000", "M")))));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S12.2: singleLotRequired SET(false) on false is NO_CHANGE, SET(true) applies")
    void falseIsAValue() {
      OrderEditSnapshot b0 = start();

      assertNoChange(merge(b0, b0, lines(update(L1, mine(LINE_SINGLE_LOT_REQUIRED, false)))));
      Result set = merge(b0, b0, lines(update(L1, mine(LINE_SINGLE_LOT_REQUIRED, true))));
      assertThat(set.conflicts()).isEmpty();
      assertThat(set.lineChanges().get(L1)).containsExactly(entry(LINE_SINGLE_LOT_REQUIRED, true));
      assertThat(OrderEditMerge.same(false, null)).isFalse();
    }

    @Test
    @DisplayName(
        "LINE-PREFERENCES-1: shipmentPreference AS_READY on the default is NO_CHANGE;"
            + " WHEN_COMPLETE applies")
    void shipmentPreferenceDefaultIsAValue() {
      OrderEditSnapshot b0 = start();

      assertNoChange(
          merge(
              b0,
              b0,
              lines(update(L1, mine(LINE_SHIPMENT_PREFERENCE, LineShipmentPreference.AS_READY)))));
      Result set =
          merge(
              b0,
              b0,
              lines(
                  update(
                      L1, mine(LINE_SHIPMENT_PREFERENCE, LineShipmentPreference.WHEN_COMPLETE))));
      assertThat(set.conflicts()).isEmpty();
      assertThat(set.lineChanges().get(L1))
          .containsExactly(entry(LINE_SHIPMENT_PREFERENCE, LineShipmentPreference.WHEN_COMPLETE));
    }

    @Test
    @DisplayName(
        "LINE-PREFERENCES-1: a stale form never reverts a shipment preference changed on the"
            + " server")
    void staleShipmentPreferenceKeepsTheServerChange() {
      OrderEditSnapshot b0 = start();
      OrderEditSnapshot current =
          withLine(b0, L1, LINE_SHIPMENT_PREFERENCE, LineShipmentPreference.WHEN_COMPLETE);

      // The form still holds the base value: that is no instruction, so the server's change stays.
      Result result =
          merge(
              b0,
              current,
              lines(update(L1, mine(LINE_SHIPMENT_PREFERENCE, LineShipmentPreference.AS_READY))));

      assertThat(result.lineChanges().getOrDefault(L1, Map.of())).isEmpty();
      assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("S12.3: tolerance 0/0 on a line without tolerance applies; zero is not a clear")
    void zeroToleranceIsAValue() {
      OrderEditSnapshot b0 = start();
      ToleranceValue zero = new ToleranceValue(BigDecimal.ZERO, BigDecimal.ZERO);

      Result result = merge(b0, b0, lines(update(L2, mine(LINE_TOLERANCE, zero))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L2)).containsExactly(entry(LINE_TOLERANCE, zero));
      assertThat(OrderEditMerge.same(zero, ToleranceValue.NONE)).isFalse();
    }

    @Test
    @DisplayName("S12.4: a zero discount is not a missing discount; the price change applies")
    void zeroDiscountIsAValue() {
      OrderEditSnapshot b0 = start();
      PricingValue zeroDiscount = new PricingValue("GBP", dec("6.5000"), BigDecimal.ZERO, null);

      Result result = merge(b0, b0, lines(update(L2, mine(LINE_PRICING, zeroDiscount))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L2)).containsExactly(entry(LINE_PRICING, zeroDiscount));
    }

    @Test
    @DisplayName("S12.5: moduleSpecs with another key order and 120.0 for 120 is NO_CHANGE")
    void moduleSpecsCompareRegardlessOfKeyOrderAndNumericForm() {
      OrderEditSnapshot base =
          withLine(
              start(), L1, LINE_SPECIFICATION, spec(specs("gsm", 120, "weave", "twill"), null));
      Map<String, Object> reordered = specs("weave", "twill", "gsm", 120.0);

      Result result =
          merge(base, base, lines(update(L1, mine(LINE_SPECIFICATION, spec(reordered, null)))));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S12.6: array order is meaningful; [B, A] against [A, B] applies")
    void arrayOrderIsMeaningful() {
      OrderEditSnapshot base =
          withLine(start(), L1, LINE_SPECIFICATION, spec(specs("yarns", List.of("A", "B")), null));
      SpecificationValue swapped = spec(specs("yarns", List.of("B", "A")), null);

      Result result = merge(base, base, lines(update(L1, mine(LINE_SPECIFICATION, swapped))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L1)).containsExactly(entry(LINE_SPECIFICATION, swapped));
    }

    @Test
    @DisplayName("S12.7: sub-keys of moduleSpecs never merge; the whole specification conflicts")
    void specificationSubKeysDoNotMerge() {
      OrderEditSnapshot base =
          withLine(
              start(), L1, LINE_SPECIFICATION, spec(specs("gsm", 120, "weave", "twill"), null));
      OrderEditSnapshot current =
          withLine(base, L1, LINE_SPECIFICATION, spec(specs("gsm", 130, "weave", "twill"), null));
      SpecificationValue mine = spec(specs("gsm", 120, "weave", "satin"), null);

      Result result = merge(base, current, lines(update(L1, mine(LINE_SPECIFICATION, mine))));

      assertThat(result.lineChanges()).isEmpty();
      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.line(LINE_SPECIFICATION, L1));
                assertThat(conflict.slot().key()).isEqualTo("line.specification");
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
              });
    }

    @Test
    @DisplayName("S12.9: a trailing space is a different text (no trim); the change applies")
    void textIsNotTrimmed() {
      OrderEditSnapshot base = withHeader(start(), NOTES, "Urgent");

      Result result = merge(base, base, header(mine(NOTES, "Urgent ")));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(NOTES, "Urgent "));
    }

    @Test
    @DisplayName(
        "S12.10: the merge compares the domain-normalised contact; the trimmed name is NO_CHANGE")
    void normalisedContactIsNoChange() {
      OrderEditSnapshot b0 = start();
      // The applier trims " Jane Hill " before the merge (SalesOrderService.blankToNull).
      ContactValue normalised = new ContactValue("Jane Hill", "jane@example.com", null, false);

      assertNoChange(merge(b0, b0, header(mine(CONTACT, normalised))));
      // Without that normalisation the merge would see a change: it never trims by itself.
      ContactValue raw = new ContactValue(" Jane Hill ", "jane@example.com", null, false);
      assertThat(OrderEditMerge.same(raw, b0.header().contact())).isFalse();
    }

    @Test
    @DisplayName("S15.10 (merge part): a new free moduleSpecs key is a change")
    void newModuleSpecsKeyIsAChange() {
      OrderEditSnapshot base =
          withLine(start(), L1, LINE_SPECIFICATION, spec(specs("gsm", 120), null));
      SpecificationValue extended = spec(specs("gsm", 120, "newKey", 1), null);

      Result result = merge(base, base, lines(update(L1, mine(LINE_SPECIFICATION, extended))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L1)).containsKey(LINE_SPECIFICATION);
    }

    @Test
    @DisplayName("S12.1-S12.6: token equality is by value, key order free, arrays ordered")
    void tokenEquality() {
      assertThat(OrderEditMerge.same(dec("4.0000"), dec("4"))).isTrue();
      assertThat(OrderEditMerge.same(qty("1000", "M"), qty("1000.000", "M"))).isTrue();
      assertThat(OrderEditMerge.same(qty("1000", "M"), qty("1000", "KG"))).isFalse();
      assertThat(OrderEditMerge.same(specs("a", 1, "b", 2), specs("b", 2, "a", 1))).isTrue();
      assertThat(OrderEditMerge.same(List.of("A", "B"), List.of("B", "A"))).isFalse();
      assertThat(OrderEditMerge.same(BigDecimal.ZERO, null)).isFalse();
      assertThat(OrderEditMerge.same(false, null)).isFalse();
      assertThat(OrderEditMerge.same(null, null)).isTrue();
      assertThat(OrderEditMerge.same("Urgent", "Urgent ")).isFalse();
    }
  }

  // ── §13 draft while saving (merge part) ────────────────────────────────────

  @Nested
  class DraftWhileSaving {

    private final OrderEditSnapshot b0 = start();
    private final OrderEditSnapshot afterA = withHeader(b0, NOTES, "Note from Avery");

    @Test
    @DisplayName("S13.2: overwriting the other writer's note seen in nextBase applies")
    void overwritingASeenValueApplies() {
      // S13.1: B saved paymentTerms against B0 after A's note; nextBase holds both, guarded.
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, afterA);
      OrderEditSnapshot nextBase = withHeader(afterA, PAYMENT_TERMS, "60 days");

      Result result =
          OrderEditMerge.merge(
              nextBase, nextBase, guard, header(mine(NOTES, "Note from Blake")), false);

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(NOTES, "Note from Blake"));
    }

    @Test
    @DisplayName("S13.5: only the key changed by the other writer conflicts")
    void onlyTheOtherWritersKeyConflicts() {
      Result result =
          merge(b0, afterA, header(mine(PAYMENT_TERMS, "60 days", NOTES, "Note from Blake")));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.header(NOTES));
                assertThat(conflict.base()).isNull();
                assertThat(conflict.current()).isEqualTo("Note from Avery");
                assertThat(conflict.mine()).isEqualTo("Note from Blake");
              });
    }

    @Test
    @DisplayName("S13.6: KEEP_CURRENT on notes with the pending paymentTerms applies paymentTerms")
    void keepCurrentWithPendingKeyApplies() {
      Slot notes = Slot.header(NOTES);
      List<RecordedConflict> recorded =
          merge(b0, afterA, header(mine(PAYMENT_TERMS, "60 days", NOTES, "Note from Blake")))
              .conflicts()
              .stream()
              .map(OrderEditMergeTest::recorded)
              .toList();
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, afterA);
      Instructions instructions = request(mine(PAYMENT_TERMS, "60 days"), List.of(), Set.of(notes));

      assertThat(
              OrderEditMerge.resolutionMismatch(
                  recorded, Map.of(notes, Choice.KEEP_CURRENT), instructions))
          .isEmpty();
      Result result = OrderEditMerge.merge(afterA, afterA, guard, instructions, false);
      assertThat(result.conflicts()).isEmpty();
      assertThat(result.headerChanges()).containsExactly(entry(PAYMENT_TERMS, "60 days"));
    }

    @Test
    @DisplayName("S13.10: CLEAR from a stale form against a SAVED nextBase is UNCONFIRMED_REVERT")
    void staleFormAgainstSavedBaseIsUnconfirmedRevert() {
      Map<Slot, String> guard = OrderEditMerge.guardBetween(b0, afterA);
      OrderEditSnapshot nextBase = withHeader(afterA, PAYMENT_TERMS, "60 days");

      Result result =
          OrderEditMerge.merge(nextBase, nextBase, guard, header(mine(NOTES, null)), false);

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.reason()).isEqualTo(Reason.UNCONFIRMED_REVERT);
                assertThat(conflict.base()).isEqualTo("Note from Avery");
                assertThat(conflict.current()).isEqualTo("Note from Avery");
                assertThat(conflict.mine()).isNull();
              });
    }
  }

  // ── §16 requirement profile (merge part) ───────────────────────────────────

  @Nested
  class RequirementProfile {

    private final ProfileRef f1 = new ProfileRef(PROFILE_ID, 2, "F1");
    private final OrderEditSnapshot base =
        withLine(start(), L1, LINE_SPECIFICATION, spec(specs("gsm", 120), f1));

    /** What the applier builds for a resolved input: no identity or version, its fingerprint. */
    private SpecificationValue resolvedTo(String fingerprint) {
      return spec(specs("gsm", 120), new ProfileRef(null, null, fingerprint));
    }

    private OrderEditSnapshot currentWith(String fingerprint, int version) {
      return withLine(
          base,
          L1,
          LINE_SPECIFICATION,
          spec(specs("gsm", 120), new ProfileRef(PROFILE_ID, version, fingerprint)));
    }

    @Test
    @DisplayName("S16.1: input resolving to the base fingerprint F1 is NO_CHANGE")
    void sameFingerprintIsNoChange() {
      Result result =
          merge(base, base, lines(update(L1, mine(LINE_SPECIFICATION, resolvedTo("F1")))));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S16.2: input resolving to the F2 already saved by A is NO_CHANGE")
    void sameAsCurrentIsNoChange() {
      Result result =
          merge(
              base,
              currentWith("F2", 3),
              lines(update(L1, mine(LINE_SPECIFICATION, resolvedTo("F2")))));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S12.8, S16.3: F1 to F2 on the server and F3 by B is CHANGED_ON_SERVER")
    void differentFingerprintsConflict() {
      SpecificationValue mine = resolvedTo("F3");

      Result result =
          merge(base, currentWith("F2", 3), lines(update(L1, mine(LINE_SPECIFICATION, mine))));

      assertThat(result.conflicts())
          .singleElement()
          .satisfies(
              conflict -> {
                assertThat(conflict.slot()).isEqualTo(Slot.line(LINE_SPECIFICATION, L1));
                assertThat(conflict.reason()).isEqualTo(Reason.CHANGED_ON_SERVER);
                assertThat(((SpecificationValue) conflict.base()).profileFingerprint())
                    .isEqualTo("F1");
                assertThat(((SpecificationValue) conflict.current()).profileFingerprint())
                    .isEqualTo("F2");
                assertThat(((SpecificationValue) conflict.mine()).profileFingerprint())
                    .isEqualTo("F3");
              });
    }

    @Test
    @DisplayName(
        "S16.4: the same semantic profile with other identity, version and key order is"
            + " NO_CHANGE")
    void profileComparesByFingerprintOnly() {
      SpecificationValue sameSemantics =
          spec(specs("gsm", 120.0), new ProfileRef(UUID.randomUUID(), 9, "F1"));

      Result result = merge(base, base, lines(update(L1, mine(LINE_SPECIFICATION, sameSemantics))));

      assertNoChange(result);
    }

    @Test
    @DisplayName("S16.5: facets never merge; A's X and B's Y conflict as the whole key")
    void facetsDoNotMerge() {
      // A changed facet X (F1 -> F2); B's partial input changed only Y and resolved to F3.
      Result result =
          merge(
              base,
              currentWith("F2", 3),
              lines(update(L1, mine(LINE_SPECIFICATION, resolvedTo("F3")))));

      assertThat(result.lineChanges()).isEmpty();
      assertThat(result.conflicts())
          .extracting(Conflict::slot)
          .containsExactly(Slot.line(LINE_SPECIFICATION, L1));
    }

    @Test
    @DisplayName("S16.9: a profile-less line changes by moduleSpecs alone")
    void profileLessLineChangesByModuleSpecs() {
      OrderEditSnapshot plain =
          withLine(start(), L1, LINE_SPECIFICATION, spec(specs("gsm", 120), null));
      SpecificationValue heavier = spec(specs("gsm", 140), null);

      Result result = merge(plain, plain, lines(update(L1, mine(LINE_SPECIFICATION, heavier))));

      assertThat(result.conflicts()).isEmpty();
      assertThat(result.lineChanges().get(L1)).containsExactly(entry(LINE_SPECIFICATION, heavier));
      assertThat(heavier.profileFingerprint()).isNull();
    }
  }

  // ── §5.8 stale-revert guard ────────────────────────────────────────────────

  @Nested
  class Guard {

    @Test
    @DisplayName("S9.6: guardBetween lists header keys another writer changed, with parent token")
    void guardListsChangedHeaderKeys() {
      OrderEditSnapshot parent = start();
      OrderEditSnapshot child =
          withHeader(withHeader(parent, NOTES, "Urgent"), PAYMENT_TERMS, "45 days");

      Map<Slot, String> guard = OrderEditMerge.guardBetween(parent, child);

      assertThat(guard)
          .containsOnly(
              entry(Slot.header(NOTES), OrderEditMerge.token(null)),
              entry(Slot.header(PAYMENT_TERMS), OrderEditMerge.token("30 days")));
    }

    @Test
    @DisplayName("S9.6: guardBetween lists line keys of lines present in both snapshots")
    void guardListsChangedLineKeys() {
      OrderEditSnapshot parent = start();
      PricingValue before = parent.line(L1).orElseThrow().pricing();
      OrderEditSnapshot child =
          withLine(parent, L1, LINE_PRICING, new PricingValue("GBP", dec("4.10"), null, null));

      assertThat(OrderEditMerge.guardBetween(parent, child))
          .containsOnly(entry(Slot.line(LINE_PRICING, L1), OrderEditMerge.token(before)));
    }

    @Test
    @DisplayName("S9.8, S12.1: equal snapshots and value-equal decimals produce no guard")
    void noGuardWithoutRealChange() {
      OrderEditSnapshot parent = start();
      OrderEditSnapshot child = withLine(parent, L1, LINE_QUANTITY, qty("1000.000", "M"));

      assertThat(OrderEditMerge.guardBetween(parent, parent)).isEmpty();
      assertThat(OrderEditMerge.guardBetween(parent, child)).isEmpty();
    }

    @Test
    @DisplayName("S6.4, S7.3: added and removed lines are not guarded")
    void addedAndRemovedLinesAreNotGuarded() {
      OrderEditSnapshot parent = start();
      OrderEditSnapshot child =
          plus(without(parent, L2), plainLine(L5, P5, qty("50", "M"), null, null, "x"));

      assertThat(OrderEditMerge.guardBetween(parent, child)).isEmpty();
    }

    @Test
    @DisplayName("S13.2: a guarded key is only an UNCONFIRMED_REVERT when mine is the parent value")
    void guardFiresOnlyOnTheParentValue() {
      OrderEditSnapshot parent = start();
      OrderEditSnapshot child = withHeader(parent, PAYMENT_TERMS, "45 days");
      Map<Slot, String> guard = OrderEditMerge.guardBetween(parent, child);

      Result revert =
          OrderEditMerge.merge(child, child, guard, header(mine(PAYMENT_TERMS, "30 days")), false);
      Result other =
          OrderEditMerge.merge(child, child, guard, header(mine(PAYMENT_TERMS, "60 days")), false);

      assertThat(revert.conflicts())
          .extracting(Conflict::reason)
          .containsExactly(Reason.UNCONFIRMED_REVERT);
      assertThat(other.conflicts()).isEmpty();
      assertThat(other.headerChanges()).containsExactly(entry(PAYMENT_TERMS, "60 days"));
    }
  }

  // ── fixture ────────────────────────────────────────────────────────────────

  /** The CEDIT-02 common start at v7. */
  private static OrderEditSnapshot start() {
    Header header =
        new Header(
            null,
            LocalDate.of(2026, 10, 1),
            RequestedDateValue.UNKNOWN,
            new DeliveryTermsValue(
                DeliveryTerm.FCA,
                "Leeds",
                IncotermsVersion.INCOTERMS_2020,
                DeliveryTermStatus.PROPOSED,
                null),
            "30 days",
            AgreementValue.NONE,
            new ContactValue("Jane Hill", "jane@example.com", null, false),
            null,
            null,
            null,
            null,
            null);
    Line l1 =
        plainLine(
            L1,
            P1,
            qty("1000", "M"),
            new PricingValue("GBP", dec("4.0000"), null, null),
            new ToleranceValue(dec("5"), dec("5")),
            L1_DIGEST);
    Line l2 =
        plainLine(
            L2,
            P2,
            qty("500", "M"),
            new PricingValue("GBP", dec("6.5000"), null, null),
            null,
            L2_DIGEST);
    return new OrderEditSnapshot(OrderEditSnapshot.SCHEMA, 7, header, List.of(l1, l2));
  }

  private static Line plainLine(
      UUID lineId,
      UUID productId,
      QuantityValue quantity,
      PricingValue pricing,
      ToleranceValue tolerance,
      String digest) {
    return new Line(
        lineId,
        0,
        productId,
        null,
        null,
        WidthValue.NONE,
        null,
        false,
        LineShipmentPreference.AS_READY,
        quantity,
        pricing,
        tolerance,
        SpecificationValue.NONE,
        digest);
  }

  /** The snapshot one version later with one header key replaced. */
  private static OrderEditSnapshot withHeader(
      OrderEditSnapshot snapshot, OrderEditKey key, Object value) {
    Map<OrderEditKey, Object> values = new EnumMap<>(OrderEditKey.class);
    for (OrderEditKey each : OrderEditKey.values()) {
      if (!each.isLineKey()) {
        values.put(each, snapshot.header().value(each));
      }
    }
    values.put(key, value);
    Header header =
        new Header(
            (String) values.get(CUSTOMER_REFERENCE),
            (LocalDate) values.get(ORDER_DATE),
            (RequestedDateValue) values.get(REQUESTED_DELIVERY_DATE),
            (DeliveryTermsValue) values.get(DELIVERY_TERMS),
            (String) values.get(PAYMENT_TERMS),
            (AgreementValue) values.get(AGREEMENT_CONTEXT),
            (ContactValue) values.get(CONTACT),
            (String) values.get(SHIPPING_ADDRESS),
            (String) values.get(BILLING_ADDRESS),
            (String) values.get(SHIPPING_METHOD),
            (String) values.get(NOTES),
            (LocalDate) values.get(DEADLINE));
    return new OrderEditSnapshot(
        snapshot.schema(), snapshot.orderVersion() + 1, header, snapshot.lines());
  }

  /** The snapshot one version later with one key of one line replaced. */
  private static OrderEditSnapshot withLine(
      OrderEditSnapshot snapshot, UUID lineId, OrderEditKey key, Object value) {
    return replaceLine(
        snapshot,
        lineId,
        line -> rebuild(line, line.productId(), key, value, line.allocationDigest()));
  }

  /** A product correction: the line keeps its id, its product changes. */
  private static OrderEditSnapshot withProduct(
      OrderEditSnapshot snapshot, UUID lineId, UUID productId) {
    return replaceLine(
        snapshot, lineId, line -> rebuild(line, productId, null, null, line.allocationDigest()));
  }

  /** A delivery-panel change: only the line's allocation digest moves. */
  private static OrderEditSnapshot withAllocation(
      OrderEditSnapshot snapshot, UUID lineId, String digest) {
    return replaceLine(
        snapshot, lineId, line -> rebuild(line, line.productId(), null, null, digest));
  }

  private static OrderEditSnapshot without(OrderEditSnapshot snapshot, UUID lineId) {
    return new OrderEditSnapshot(
        snapshot.schema(),
        snapshot.orderVersion() + 1,
        snapshot.header(),
        snapshot.lines().stream().filter(line -> !line.lineId().equals(lineId)).toList());
  }

  private static OrderEditSnapshot plus(OrderEditSnapshot snapshot, Line added) {
    List<Line> lines = new java.util.ArrayList<>(snapshot.lines());
    lines.add(added);
    return new OrderEditSnapshot(
        snapshot.schema(), snapshot.orderVersion() + 1, snapshot.header(), lines);
  }

  private static OrderEditSnapshot replaceLine(
      OrderEditSnapshot snapshot, UUID lineId, UnaryOperator<Line> change) {
    return new OrderEditSnapshot(
        snapshot.schema(),
        snapshot.orderVersion() + 1,
        snapshot.header(),
        snapshot.lines().stream()
            .map(line -> line.lineId().equals(lineId) ? change.apply(line) : line)
            .toList());
  }

  private static Line rebuild(
      Line line, UUID productId, OrderEditKey key, Object value, String digest) {
    Map<OrderEditKey, Object> values = new EnumMap<>(OrderEditKey.class);
    for (OrderEditKey each : OrderEditKey.values()) {
      if (each.isLineKey()) {
        values.put(each, line.value(each));
      }
    }
    if (key != null) {
      values.put(key, value);
    }
    return new Line(
        line.lineId(),
        line.lineVersion() + 1,
        productId,
        (String) values.get(LINE_PRODUCT_DESC),
        (UUID) values.get(LINE_COLOR),
        (WidthValue) values.get(LINE_FINISHED_WIDTH),
        (LocalDate) values.get(LINE_REQUESTED_DELIVERY_DATE),
        (Boolean) values.get(LINE_SINGLE_LOT_REQUIRED),
        (LineShipmentPreference) values.get(LINE_SHIPMENT_PREFERENCE),
        (QuantityValue) values.get(LINE_QUANTITY),
        (PricingValue) values.get(LINE_PRICING),
        (ToleranceValue) values.get(LINE_TOLERANCE),
        (SpecificationValue) values.get(LINE_SPECIFICATION),
        digest);
  }

  // ── request helpers ────────────────────────────────────────────────────────

  /** Mine by key, in the given order; a null value is a cleared simple key. */
  private static Map<OrderEditKey, Object> mine(Object... keysAndValues) {
    Map<OrderEditKey, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      values.put((OrderEditKey) keysAndValues[i], keysAndValues[i + 1]);
    }
    return values;
  }

  private static Map<String, Object> specs(Object... keysAndValues) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      values.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return values;
  }

  private static SpecificationValue spec(Map<String, Object> moduleSpecs, ProfileRef profile) {
    return new SpecificationValue(ModuleType.FABRIC, moduleSpecs, profile);
  }

  private static Update update(UUID lineId, Map<OrderEditKey, Object> mine) {
    return new Update(lineId, mine);
  }

  private static Instructions header(Map<OrderEditKey, Object> mine) {
    return new Instructions(mine, List.of(), Set.of());
  }

  private static Instructions lines(LineInstruction... instructions) {
    return new Instructions(Map.of(), List.of(instructions), Set.of());
  }

  private static Instructions request(
      Map<OrderEditKey, Object> header, List<LineInstruction> lines, Set<Slot> resolved) {
    return new Instructions(header, lines, resolved);
  }

  private static Result merge(
      OrderEditSnapshot base, OrderEditSnapshot current, Instructions instructions) {
    return OrderEditMerge.merge(base, current, Map.of(), instructions, false);
  }

  private static RecordedConflict recorded(Conflict conflict) {
    return new RecordedConflict(
        conflict.slot(),
        conflict.reason(),
        conflict.choices(),
        conflict.mineToken(),
        conflict.instructionToken());
  }

  private static void assertNoChange(Result result) {
    assertThat(result.conflicts()).isEmpty();
    assertThat(result.hasChanges()).isFalse();
    assertThat(result.headerChanges()).isEmpty();
    assertThat(result.lineChanges()).isEmpty();
    assertThat(result.removals()).isEmpty();
    assertThat(result.additions()).isEmpty();
  }

  private static QuantityValue qty(String value, String unit) {
    return new QuantityValue(dec(value), unit);
  }

  private static BigDecimal dec(String value) {
    return new BigDecimal(value);
  }
}
