package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseProperties;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseRetentionJob;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.app.QuantityAcceptanceService;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Grant;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Holder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.Requirement;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos.RequirementReason;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseField;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/**
 * Field leases of a sales order (CEDIT-07 §6) on real PostgreSQL and a real HTTP server: atomic
 * ownership, tokens per ownership period, the save's proof inside its transaction, the policy of
 * the other writers, the live signal and tenant isolation. Races are ordered with latches and the
 * database's own lock-wait signal, never with sleeps; time moves only through the test clock.
 *
 * <p>Fixture (see {@link SalesOrderEditItSupport}): tenant with actors A, B, C and order O with
 * lines L1 and L2. Leases are always enforced (CEDIT-07-F3). Every save here carries the proof the
 * test gives it, or none: the automatic proof of {@link SalesOrderEditItSupport} is off.
 */
class SalesOrderEditLeaseIT extends SalesOrderLiveItSupport {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Autowired private SalesOrderEditLeaseService leaseApi;
  @Autowired private SalesOrderEditSessionService sessions;
  @Autowired private LiveEditLeaseProperties leaseProperties;
  @Autowired private LiveEditLeaseRetentionJob leaseRetention;
  @Autowired private OrderPartiesService parties;
  @Autowired private QuantityAcceptanceService quantityAcceptances;
  @Autowired private ProductCorrectionService productCorrections;
  @Autowired private OrderFlowService flows;
  @Autowired private SalesOrderService salesOrders;

  private ExecutorService pool;
  private int maxPerSession;
  private int maxPerResource;

  /** Every save here proves what the test gives it, or nothing. */
  @Override
  protected boolean proveLeases() {
    return false;
  }

  @BeforeEach
  void startPool() {
    pool = Executors.newFixedThreadPool(4);
    maxPerSession = leaseProperties.getMaxLeasesPerSession();
    maxPerResource = leaseProperties.getMaxLeasesPerResource();
  }

  @AfterEach
  void stopPool() {
    pool.shutdownNow();
    leaseProperties.setMaxLeasesPerSession(maxPerSession);
    leaseProperties.setMaxLeasesPerResource(maxPerResource);
  }

  // ── L01–L04: ownership ────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "L01 (CE-05): two sessions race for one key in both orders; one wins, no token leaks")
  void oneWinnerInBothOrders() throws Exception {
    UUID tabA = openSession(actorA);
    UUID tabB = openSession(actorB);

    for (boolean aFirst : List.of(true, false)) {
      Actor first = aFirst ? actorA : actorB;
      Actor second = aFirst ? actorB : actorA;
      UUID firstTab = aFirst ? tabA : tabB;
      UUID secondTab = aFirst ? tabB : tabA;
      CountDownLatch granted = new CountDownLatch(1);
      CountDownLatch commit = new CountDownLatch(1);
      Future<Object> winner =
          pool.submit(
              () ->
                  as(
                      first,
                      () ->
                          transactions.execute(
                              status -> {
                                Object result =
                                    leaseApi.acquire(
                                        orderId, request(firstTab, notes()), first.id());
                                granted.countDown();
                                await(commit);
                                return result;
                              })));
      await(granted);
      Future<Object> loser = pool.submit(() -> acquire(second, secondTab, notes()));
      awaitLockWaiter();
      commit.countDown();

      Grant grant = (Grant) winner.get(30, TimeUnit.SECONDS);
      Object refused = loser.get(30, TimeUnit.SECONDS);
      assertThat(failureCode(refused)).isEqualTo("EDIT_LEASE_UNAVAILABLE");
      List<Holder> holders = holders(refused);
      assertThat(holders)
          .singleElement()
          .satisfies(h -> assertThat(h.userId()).isEqualTo(first.id()));
      assertThat(holders.getFirst().editSessionId()).isNull();
      assertThat(objectMapper.writeValueAsString(((DomainException) refused).getDetails()))
          .doesNotContain(grant.leases().getFirst().leaseToken().toString());
      release(first, firstTab, grant.leases().getFirst().leaseToken());
    }
  }

  @Test
  @DisplayName(
      "L02 (CE-10): different keys and lines are held together; two tabs of one person collide")
  void differentKeysCoexistButTabsCollide() {
    UUID tabA = openSession(actorA);
    UUID tabA2 = openSession(actorA);
    UUID tabB = openSession(actorB);

    granted(actorA, tabA, notes(), line(SalesOrderEditLeaseField.LINE_PRICING, l1));
    granted(
        actorB,
        tabB,
        header(SalesOrderEditLeaseField.PAYMENT_TERMS),
        line(SalesOrderEditLeaseField.LINE_PRICING, l2));
    granted(actorB, tabB, line(SalesOrderEditLeaseField.LINE_QUANTITY, l1));

    Object otherTab = acquire(actorA, tabA2, notes());
    assertThat(failureCode(otherTab)).isEqualTo("EDIT_LEASE_UNAVAILABLE");
    Holder mine = holders(otherTab).getFirst();
    assertThat(mine.mine()).isTrue();
    assertThat(mine.editSessionId()).isEqualTo(tabA);
    assertThat(leases(actorC)).hasSize(5);
  }

  @Test
  @DisplayName(
      "L03: header/line mix-ups, foreign lines, repeats and parts of a composite are refused")
  void keysAreChecked() throws Exception {
    UUID tab = openSession(actorA);

    assertThat(
            failureCode(
                acquire(
                    actorA, tab, new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.NOTES, l1))))
        .isEqualTo("EDIT_LEASE_KEY_INVALID");
    assertThat(
            failureCode(
                acquire(
                    actorA,
                    tab,
                    new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.LINE_PRICING, null))))
        .isEqualTo("EDIT_LEASE_KEY_INVALID");
    OtherTenant other = otherTenantWithOrder();
    Object foreign =
        acquire(actorA, tab, line(SalesOrderEditLeaseField.LINE_PRICING, UUID.randomUUID()));
    assertThat(failureCode(foreign)).isEqualTo("EDIT_LEASE_KEY_INVALID");
    assertThat(((DomainException) foreign).getMessage()).doesNotContain(other.orderId().toString());
    assertThat(failureCode(acquire(actorA, tab, notes(), notes())))
        .isEqualTo("EDIT_LEASE_KEY_INVALID");

    String body =
        "{\"editSessionId\":\""
            + tab
            + "\",\"keys\":[{\"key\":\"line.pricing.currency\",\"lineId\":\""
            + l1
            + "\"}]}";
    assertThat(http("POST", leasesPath(), actorA, body).status()).isEqualTo(400);
    assertThat(leases(actorA)).isEmpty();
  }

  @Test
  @DisplayName("L04 (CE-16): removing a line and changing one of its keys race in both orders")
  void wholeLineAndFieldRace() throws Exception {
    UUID tabA = openSession(actorA);
    UUID tabB = openSession(actorB);
    SalesOrderEditLeaseKey whole = line(SalesOrderEditLeaseField.LINE, l1);
    SalesOrderEditLeaseKey quantity = line(SalesOrderEditLeaseField.LINE_QUANTITY, l1);

    for (boolean wholeFirst : List.of(true, false)) {
      SalesOrderEditLeaseKey firstKey = wholeFirst ? whole : quantity;
      SalesOrderEditLeaseKey secondKey = wholeFirst ? quantity : whole;
      CountDownLatch granted = new CountDownLatch(1);
      CountDownLatch commit = new CountDownLatch(1);
      Future<Object> winner =
          pool.submit(
              () ->
                  as(
                      actorA,
                      () ->
                          transactions.execute(
                              status -> {
                                Object result =
                                    leaseApi.acquire(orderId, request(tabA, firstKey), actorA.id());
                                granted.countDown();
                                await(commit);
                                return result;
                              })));
      await(granted);
      Future<Object> loser = pool.submit(() -> acquire(actorB, tabB, secondKey));
      awaitLockWaiter();
      commit.countDown();

      Grant grant = (Grant) winner.get(30, TimeUnit.SECONDS);
      assertThat(failureCode(loser.get(30, TimeUnit.SECONDS))).isEqualTo("EDIT_LEASE_UNAVAILABLE");
      assertThat(leases(actorC))
          .singleElement()
          .satisfies(h -> assertThat(h.userId()).isEqualTo(actorA.id()));
      release(actorA, tabA, grant.leases().getFirst().leaseToken());
    }
  }

  // ── L05–L07: time, tokens and the save's serialization ────────────────────

  @Test
  @DisplayName(
      "L05 (CE-09): after expiry a new period has a new token; late renew, release and save of the"
          + " old one change nothing; the same session again and a cleaned row get new tokens too")
  void newPeriodNewToken() {
    UUID tabA = openSession(actorA);
    UUID old = token(granted(actorA, tabA, notes()));
    UUID baseId = open(actorA).baseId();

    clock.advance(Duration.ofSeconds(60));
    renewSession(actorA, tabA);
    clock.advance(Duration.ofSeconds(31));
    UUID tabB = openSession(actorB);
    UUID taken = token(granted(actorB, tabB, notes()));
    assertThat(taken).isNotEqualTo(old);

    SalesOrderEditLeaseDtos.Renewal late = renew(actorA, tabA, old);
    assertThat(late.renewed()).isEmpty();
    assertThat(late.lostLeaseTokens()).containsExactly(old);
    release(actorA, tabA, old);
    Object save =
        save(actorA, proof(body(UUID.randomUUID(), baseId, "notes", set("late")), tabA, old));
    assertThat(failureCode(save)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(leases(actorC))
        .singleElement()
        .satisfies(h -> assertThat(h.userId()).isEqualTo(actorB.id()));

    // The same session releases and takes the key again: a new period.
    release(actorB, tabB, taken);
    UUID again = token(granted(actorB, tabB, notes()));
    assertThat(again).isNotEqualTo(taken);

    // A row cleaned up and created again starts afresh too (no ABA).
    release(actorB, tabB, again);
    jdbc.update(
        "UPDATE common_infrastructure.live_edit_lease SET released_at = released_at - interval '3 days',"
            + " expires_at = expires_at - interval '3 days', renewed_at = renewed_at - interval '3 days',"
            + " acquired_at = acquired_at - interval '3 days' WHERE resource_id = ?",
        orderId);
    assertThat(leaseRetention.purge(java.time.Instant.now())).isGreaterThanOrEqualTo(1);
    UUID tabB2 = openSession(actorB);
    UUID afresh = token(granted(actorB, tabB2, notes()));
    assertThat(afresh).isNotIn(old, taken, again);
    assertThat(renew(actorB, tabB, again).lostLeaseTokens()).containsExactly(again);
  }

  @Test
  @DisplayName(
      "L06: a save that waits for the order lock while its lease expires is judged after the wait")
  void expiryWhileWaitingIsSeen() throws Exception {
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));
    UUID baseId = open(actorA).baseId();
    clock.advance(Duration.ofSeconds(60));
    renewSession(actorA, tab);

    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch unlock = new CountDownLatch(1);
    Future<Object> holder = pool.submit(() -> holdOrderRow(locked, unlock));
    await(locked);
    Future<Object> waiting =
        pool.submit(
            () ->
                save(
                    actorA,
                    proof(body(UUID.randomUUID(), baseId, "notes", set("late")), tab, token)));
    awaitLockWaiter();
    clock.advance(Duration.ofSeconds(40));
    unlock.countDown();
    holder.get(30, TimeUnit.SECONDS);

    Object result = waiting.get(30, TimeUnit.SECONDS);
    assertThat(failureCode(result)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(requirements(result))
        .singleElement()
        .satisfies(r -> assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD));
    assertThat(orderText("notes")).isNull();
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName(
      "L07: between a save's check and its commit no lease changes hands; a waiting acquire and a"
          + " waiting close see the saved, released state afterwards")
  void saveSerialisesWithTransferAndClose() throws Exception {
    UUID tabA = openSession(actorA);
    UUID token = token(granted(actorA, tabA, notes()));
    UUID baseId = open(actorA).baseId();
    UUID tabB = openSession(actorB);

    // A transaction holds L2's row: the save passes its lease check, then waits for the lines.
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch unlock = new CountDownLatch(1);
    Future<Object> holder = pool.submit(() -> holdLineRow(l2, locked, unlock));
    await(locked);
    Future<Object> saving =
        pool.submit(
            () ->
                save(
                    actorA,
                    proof(
                        body(UUID.randomUUID(), baseId, "notes", set("Saved by A")), tabA, token)));
    awaitLockWaiters(1);
    // The lease would have expired by now; the save already proved it and holds its rows.
    clock.advance(Duration.ofSeconds(120));
    UUID tabB2 = openSession(actorB);
    Future<Object> taking = pool.submit(() -> acquire(actorB, tabB2, notes()));
    Future<Object> closing =
        pool.submit(() -> as(actorA, () -> run(() -> sessions.close(orderId, tabA, actorA.id()))));
    awaitLockWaiters(3);
    unlock.countDown();
    holder.get(30, TimeUnit.SECONDS);

    SalesOrderEditResult saved = (SalesOrderEditResult) saving.get(30, TimeUnit.SECONDS);
    assertThat(saved.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isEqualTo("Saved by A");
    Grant grant = (Grant) taking.get(30, TimeUnit.SECONDS);
    assertThat(token(grant)).isNotEqualTo(token);
    assertThat(closing.get(30, TimeUnit.SECONDS)).isEqualTo(Boolean.TRUE);
    assertThat(leases(actorC))
        .singleElement()
        .satisfies(h -> assertThat(h.userId()).isEqualTo(actorB.id()));
    assertThat(tabB).isNotEqualTo(tabB2);
  }

  // ── L08–L11: the save ─────────────────────────────────────────────────────

  @Test
  @DisplayName("L08 (CE-13): one key without its lease: nothing changes, no history, no receipt")
  void oneMissingLeaseSavesNothing() {
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));
    UUID baseId = open(actorA).baseId();

    Object result =
        save(
            actorA,
            proof(
                body(UUID.randomUUID(), baseId, "notes", set("A"), "paymentTerms", set("60 days")),
                tab,
                token));

    assertThat(failureCode(result)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(requirements(result))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.key()).isEqualTo(SalesOrderEditLeaseField.PAYMENT_TERMS);
              assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD);
              assertThat(r.holder()).isNull();
            });
    assertThat(orderText("notes")).isNull();
    assertThat(orderText("payment_terms")).isEqualTo("30 days");
    assertThat(historyRows()).isZero();
    assertThat(receipts()).isZero();
    assertThat(leases(actorA))
        .singleElement()
        .satisfies(h -> assertThat(h.key()).isEqualTo(SalesOrderEditLeaseField.NOTES));
  }

  @Test
  @DisplayName("L09: a successful save releases the leases it used and nothing else")
  void successReleasesOnlyUsedLeases() {
    UUID tabA = openSession(actorA);
    UUID tabB = openSession(actorB);
    Grant mine = granted(actorA, tabA, notes(), header(SalesOrderEditLeaseField.PAYMENT_TERMS));
    granted(actorB, tabB, line(SalesOrderEditLeaseField.LINE_PRICING, l2));
    UUID baseId = open(actorA).baseId();

    SalesOrderEditResult result =
        saved(
            actorA,
            proof(
                body(UUID.randomUUID(), baseId, "notes", set("A")),
                tabA,
                token(mine, SalesOrderEditLeaseField.NOTES)));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(leases(actorC))
        .extracting(Holder::key)
        .containsExactlyInAnyOrder(
            SalesOrderEditLeaseField.PAYMENT_TERMS, SalesOrderEditLeaseField.LINE_PRICING);
  }

  @Test
  @DisplayName(
      "L10 (CE-20): a lost answer is replayed after the lease was released and taken by another;"
          + " no second release, no new history")
  void replayAfterTheLeaseMoved() {
    UUID tabA = openSession(actorA);
    UUID token = token(granted(actorA, tabA, notes()));
    UUID baseId = open(actorA).baseId();
    Map<String, Object> body =
        proof(body(UUID.randomUUID(), baseId, "notes", set("A")), tabA, token);
    SalesOrderEditResult first = saved(actorA, body);
    int history = historyRows();

    UUID tabB = openSession(actorB);
    UUID theirs = token(granted(actorB, tabB, notes()));

    SalesOrderEditResult again = saved(actorA, body);
    assertThat(again.replayed()).isTrue();
    assertThat(again.outcome()).isEqualTo(first.outcome());
    assertThat(again.resultVersion()).isEqualTo(first.resultVersion());
    assertThat(historyRows()).isEqualTo(history);
    assertThat(leases(actorC))
        .singleElement()
        .satisfies(h -> assertThat(h.userId()).isEqualTo(actorB.id()));
    assertThat(renew(actorB, tabB, theirs).renewed()).hasSize(1);
  }

  @Test
  @DisplayName(
      "L11: re-acquire and resend under the same operation id; other content is refused; NO_CHANGE"
          + " releases; conflict and validation failure keep the lease; USE_MINE saves with it")
  void saveOutcomesAndTheLease() {
    UUID tabA = openSession(actorA);
    UUID baseId = open(actorA).baseId();
    UUID operation = UUID.randomUUID();

    // Refused without a lease, nothing recorded: the same operation id goes through once held.
    Object refused = save(actorA, proof(body(operation, baseId, "notes", set("A")), tabA));
    assertThat(failureCode(refused)).isEqualTo("EDIT_LEASE_REQUIRED");
    UUID token = token(granted(actorA, tabA, notes()));
    assertThat(
            saved(actorA, proof(body(operation, baseId, "notes", set("A")), tabA, token))
                .replayed())
        .isFalse();

    // The same id with other content stays refused, whatever the proof.
    UUID token2 = token(granted(actorA, tabA, notes()));
    assertThat(
            failureCode(
                save(actorA, proof(body(operation, baseId, "notes", set("Other")), tabA, token2))))
        .isEqualTo("OPERATION_ID_REUSED");

    // A surplus token and tokens without a session are client errors.
    UUID base2 = open(actorA).baseId();
    assertThat(
            failureCode(
                save(
                    actorA,
                    proof(
                        body(UUID.randomUUID(), base2, "notes", set("A2")),
                        tabA,
                        token2,
                        UUID.randomUUID()))))
        .isEqualTo("EDIT_LEASE_TOKEN_UNEXPECTED");
    Map<String, Object> noSession = body(UUID.randomUUID(), base2, "notes", set("A2"));
    noSession.put("leaseTokens", List.of(token2));
    assertThat(failureCode(save(actorA, noSession))).isEqualTo("VALIDATION_ERROR");

    // NO_CHANGE releases the lease it used.
    UUID terms = token(granted(actorA, tabA, header(SalesOrderEditLeaseField.PAYMENT_TERMS)));
    SalesOrderEditResult same =
        saved(
            actorA,
            proof(body(UUID.randomUUID(), base2, "paymentTerms", set("30 days")), tabA, terms));
    assertThat(same.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(leases(actorA))
        .extracting(Holder::key)
        .containsExactly(SalesOrderEditLeaseField.NOTES);

    // A refused request (422 before the transaction) and a domain failure inside it (L2 below
    // its 300 M allocation, after the lease check) keep the lease and record nothing.
    UUID contact = token(granted(actorA, tabA, header(SalesOrderEditLeaseField.CONTACT)));
    int receiptsBefore = receipts();
    int historyBefore = historyRows();
    Object invalid =
        save(
            actorA,
            proof(
                body(
                    UUID.randomUUID(),
                    base2,
                    "contact",
                    set(pairs("name", "Jane Hill", "whatsapp", true))),
                tabA,
                contact));
    assertThat(failureCode(invalid)).isEqualTo("CONTACT_WHATSAPP_NEEDS_PHONE");
    UUID l2Quantity =
        token(granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_QUANTITY, l2)));
    Object belowAllocation =
        save(
            actorA,
            proof(
                withLines(
                    body(UUID.randomUUID(), base2),
                    List.of(update(l2, "quantity", set(quantity("100", "M"))))),
                tabA,
                l2Quantity));
    assertThat(belowAllocation).isInstanceOf(DomainException.class);
    assertThat(failureCode(belowAllocation)).doesNotStartWith("EDIT_LEASE");
    assertThat(receipts()).isEqualTo(receiptsBefore);
    assertThat(historyRows()).isEqualTo(historyBefore);
    assertThat(leases(actorA))
        .extracting(Holder::key)
        .contains(SalesOrderEditLeaseField.CONTACT, SalesOrderEditLeaseField.LINE_QUANTITY);

    // A conflict keeps the lease; USE_MINE with the same token saves and releases it.
    UUID stale = open(actorA).baseId();
    UUID tabB = openSession(actorB);
    release(actorA, tabA, token2);
    UUID bNotes = token(granted(actorB, tabB, notes()));
    saved(
        actorB,
        proof(body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("B")), tabB, bNotes));
    UUID aNotes = token(granted(actorA, tabA, notes()));
    JsonNode conflict =
        conflicted(
            actorA, proof(body(UUID.randomUUID(), stale, "notes", set("Mine")), tabA, aNotes));
    assertThat(conflict.path("code").asText()).isEqualTo("EDIT_CONFLICT");
    assertThat(leases(actorA)).extracting(Holder::key).contains(SalesOrderEditLeaseField.NOTES);
    UUID resolveAgainst = UUID.fromString(conflict.path("currentBase").path("baseId").asText());
    SalesOrderEditResult resolved =
        saved(
            actorA,
            proof(
                withResolutions(
                    body(UUID.randomUUID(), resolveAgainst, "notes", set("Mine")),
                    List.of(resolution("notes", null, "USE_MINE"))),
                tabA,
                aNotes));
    assertThat(resolved.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isEqualTo("Mine");
    assertThat(leases(actorA))
        .extracting(Holder::key)
        .doesNotContain(SalesOrderEditLeaseField.NOTES);
  }

  // ── L12–L13: access ───────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "L12: closing a session ends its leases; an inactive user, a suspended tenant or lost"
          + " permission acquires, renews and saves nothing; release needs read only")
  void accessLossStopsLeases() {
    UUID tabA = openSession(actorA);
    granted(actorA, tabA, notes());
    as(actorA, () -> run(() -> sessions.close(orderId, tabA, actorA.id())));
    assertThat(leases(actorB)).isEmpty();
    assertThat(acquire(actorA, tabA, notes())).isInstanceOf(LiveEditSessionNotFoundException.class);

    UUID tabC = openSession(actorC);
    UUID cToken = token(granted(actorC, tabC, notes()));
    readOnlyUsers.add(actorC.id());
    assertThat(acquire(actorC, tabC, header(SalesOrderEditLeaseField.DEADLINE)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(as(actorC, () -> leaseApi.renew(orderId, tokens(tabC, cToken), actorC.id())))
        .isInstanceOf(AccessDeniedException.class);
    release(actorC, tabC, cToken);
    assertThat(leases(actorB)).isEmpty();
    readOnlyUsers.clear();

    UUID tabB = openSession(actorB);
    UUID bToken = token(granted(actorB, tabB, notes()));
    UUID baseId = open(actorB).baseId();
    jdbc.update("UPDATE common_user.common_user SET is_active = false WHERE id = ?", actorB.id());
    assertThat(acquire(actorB, tabB, header(SalesOrderEditLeaseField.DEADLINE)))
        .isInstanceOf(NotFoundException.class);
    assertThat(as(actorB, () -> leaseApi.renew(orderId, tokens(tabB, bToken), actorB.id())))
        .isInstanceOf(NotFoundException.class);
    assertThat(
            save(actorB, proof(body(UUID.randomUUID(), baseId, "notes", set("B")), tabB, bToken)))
        .isInstanceOf(NotFoundException.class);
    jdbc.update("UPDATE common_user.common_user SET is_active = true WHERE id = ?", actorB.id());

    jdbc.update(
        "UPDATE common_tenant.common_tenant SET status = 'SUSPENDED' WHERE id = ?", tenantId);
    assertThat(acquire(actorB, tabB, header(SalesOrderEditLeaseField.DEADLINE)))
        .isInstanceOf(NotFoundException.class);
    assertThat(
            save(actorB, proof(body(UUID.randomUUID(), baseId, "notes", set("B")), tabB, bToken)))
        .isInstanceOf(NotFoundException.class);
    jdbc.update("UPDATE common_tenant.common_tenant SET status = 'ACTIVE' WHERE id = ?", tenantId);

    revoke(actorB);
    assertThat(acquire(actorB, tabB, header(SalesOrderEditLeaseField.DEADLINE)))
        .isInstanceOf(NotFoundException.class);
    assertThat(orderText("notes")).isNull();
  }

  @Test
  @DisplayName(
      "L13 (CE-17): another tenant sees no lease row through RLS; nobody uses another's session or"
          + " tokens")
  void isolation() throws Exception {
    UUID tabA = openSession(actorA);
    UUID token = token(granted(actorA, tabA, notes()));

    assertThat(acquire(actorB, tabA, header(SalesOrderEditLeaseField.DEADLINE)))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
    assertThat(as(actorB, () -> leaseApi.renew(orderId, tokens(tabA, token), actorB.id())))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
    UUID tabB = openSession(actorB);
    assertThat(renew(actorB, tabB, token).lostLeaseTokens()).containsExactly(token);
    release(actorB, tabB, token);
    assertThat(leases(actorC)).hasSize(1);

    OtherTenant other = otherTenantWithOrder();
    assertThat(as(other.actor(), () -> leaseApi.list(orderId, other.actor().id())))
        .isInstanceOf(NotFoundException.class);
    assertThat(count(tenantId, "SELECT count(*) FROM common_infrastructure.live_edit_lease"))
        .isEqualTo(1);
    assertThat(
            count(other.tenantId(), "SELECT count(*) FROM common_infrastructure.live_edit_lease"))
        .isZero();
  }

  // ── L14–L17: other writers, state changes and the mode ────────────────────

  @Test
  @DisplayName(
      "L14 (CEDIT-07-F3): leases are always enforced with no switch: a key is granted, a"
          + " tokenless save is refused, and the legacy full replace is refused every time")
  void alwaysEnforcedAndLegacyReplace() {
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));

    UpdateSalesOrderRequest put = legacyRequest("Legacy");
    assertThat(failureCode(as(actorA, () -> salesOrders.updateOrder(orderId, actorA.id(), put))))
        .isEqualTo("LEGACY_EDIT_DISABLED");
    release(actorA, tab, token);
    // Nobody holds anything now: the full replace is refused all the same.
    UpdateSalesOrderRequest again = legacyRequest("Legacy again");
    assertThat(failureCode(as(actorA, () -> salesOrders.updateOrder(orderId, actorA.id(), again))))
        .isEqualTo("LEGACY_EDIT_DISABLED");
    assertThat(orderText("notes")).isNull();
    assertThat(
            failureCode(
                save(
                    actorA,
                    body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("No proof")))))
        .isEqualTo("EDIT_LEASE_REQUIRED");
  }

  @Test
  @DisplayName(
      "L15: a held key refuses the requested-date section, quantity acceptance and product"
          + " correction, the same person's other tab too; an unrelated lease does not")
  void otherWritersRespectLeases() {
    UUID tabA = openSession(actorA);
    UUID dates =
        token(granted(actorA, tabA, header(SalesOrderEditLeaseField.REQUESTED_DELIVERY_DATE)));
    for (Actor writer : List.of(actorB, actorA)) {
      Object refused = setRequestedDate(writer);
      assertThat(failureCode(refused)).isEqualTo("EDIT_LEASE_HELD");
      assertThat(holders(refused))
          .singleElement()
          .satisfies(h -> assertThat(h.userId()).isEqualTo(actorA.id()));
    }
    release(actorA, tabA, dates);
    granted(actorA, tabA, notes());
    assertThat(setRequestedDate(actorB)).isNotInstanceOf(RuntimeException.class);

    UUID quantity = token(granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_QUANTITY, l1)));
    assertThat(
            failureCode(
                as(
                    actorB,
                    () -> run(() -> quantityAcceptances.withdraw(orderId, l1, actorB.id())))))
        .isEqualTo("EDIT_LEASE_HELD");
    release(actorA, tabA, quantity);
    granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, l2));
    // Free of leases, the withdrawal reaches its own rule: there is no acceptance to withdraw.
    assertThat(
            failureCode(
                as(
                    actorB,
                    () -> run(() -> quantityAcceptances.withdraw(orderId, l1, actorB.id())))))
        .isNotEqualTo("EDIT_LEASE_HELD");

    UUID pricing = token(granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, l1)));
    assertThat(failureCode(correctL1(actorB))).isEqualTo("EDIT_LEASE_HELD");
    assertThat(
            jdbc.queryForObject(
                "SELECT product_id FROM sales_ord.sales_order_line WHERE id = ?", UUID.class, l1))
        .isEqualTo(p1);
    release(actorA, tabA, pricing);
    assertThat(correctL1(actorB)).isNotInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("L15: a command and an acquire race in both orders; the later one sees the earlier")
  void commandAndAcquireRace() throws Exception {
    UUID tabA = openSession(actorA);
    SalesOrderEditLeaseKey dates = header(SalesOrderEditLeaseField.REQUESTED_DELIVERY_DATE);

    // Acquire first: the command waits for the order row and is refused.
    CountDownLatch granted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    Future<Object> lease =
        pool.submit(
            () ->
                as(
                    actorA,
                    () ->
                        transactions.execute(
                            status -> {
                              Object result =
                                  leaseApi.acquire(orderId, request(tabA, dates), actorA.id());
                              granted.countDown();
                              await(commit);
                              return result;
                            })));
    await(granted);
    Future<Object> command = pool.submit(() -> setRequestedDate(actorB));
    awaitLockWaiter();
    commit.countDown();
    UUID token = token((Grant) lease.get(30, TimeUnit.SECONDS));
    assertThat(failureCode(command.get(30, TimeUnit.SECONDS))).isEqualTo("EDIT_LEASE_HELD");
    release(actorA, tabA, token);

    // Command first: the acquire waits and is then granted on the committed order. The writer
    // records "not requested": the order's date is still unknown, so clearing it would write
    // nothing
    // and leave the version where it was.
    long before = orderVersion();
    CountDownLatch written = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    Future<Object> writer =
        pool.submit(
            () ->
                as(
                    actorB,
                    () ->
                        transactions.execute(
                            status -> {
                              Object result =
                                  parties.setRequestedDate(
                                      orderId,
                                      new OrderPartyDtos.SetRequestedDateRequest(
                                          orderVersion(),
                                          new OrderPartyDtos.RequestedDateInput(
                                              RequestedDateStatus.NOT_REQUESTED, null, null, null)),
                                      actorB.id());
                              written.countDown();
                              await(finish);
                              return result;
                            })));
    await(written);
    Future<Object> later = pool.submit(() -> acquire(actorA, tabA, dates));
    awaitLockWaiter();
    finish.countDown();
    assertThat(writer.get(30, TimeUnit.SECONDS)).isNotInstanceOf(RuntimeException.class);
    assertThat(later.get(30, TimeUnit.SECONDS)).isInstanceOf(Grant.class);
    assertThat(orderVersion()).isGreaterThan(before);
  }

  @Test
  @DisplayName(
      "L16: leaving the draft voids every lease at once; back in the draft, the old token still"
          + " neither renews nor saves")
  void leavingTheDraftVoidsLeases() {
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));
    UUID baseId = open(actorA).baseId();

    assertThat(as(actorA, () -> flows.submit(orderId, actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(leases(actorB)).isEmpty();
    assertThat(failureCode(acquire(actorA, tab, notes()))).isNotEqualTo("EDIT_LEASE_UNAVAILABLE");

    assertThat(as(actorA, () -> flows.withdraw(orderId, "Customer changed the date", actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(leases(actorB)).isEmpty();
    assertThat(renew(actorA, tab, token).lostLeaseTokens()).containsExactly(token);
    assertThat(
            failureCode(
                save(
                    actorA,
                    proof(body(UUID.randomUUID(), baseId, "notes", set("Old")), tab, token))))
        .isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(token(granted(actorA, tab, notes()))).isNotEqualTo(token);
  }

  @Test
  @DisplayName(
      "L17 (CEDIT-07-F3): a tokenless old client is refused explicitly; a held key stays held for"
          + " everybody else until it is released")
  void tokenlessClientsAreRefused() {
    UUID tabA = openSession(actorA);
    UUID token = token(granted(actorA, tabA, notes()));

    Object oldClient =
        save(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("B")));
    assertThat(requirements(oldClient))
        .singleElement()
        .satisfies(r -> assertThat(r.reason()).isEqualTo(RequirementReason.HELD_BY_ANOTHER));

    release(actorA, tabA, token);
    Object stillTokenless =
        save(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("B")));
    assertThat(requirements(stillTokenless))
        .singleElement()
        .satisfies(r -> assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD));
    UUID tabB = openSession(actorB);
    UUID tokenB = token(granted(actorB, tabB, notes()));
    assertThat(
            saved(
                    actorB,
                    proof(
                        body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("B")),
                        tabB,
                        tokenB))
                .outcome())
        .isEqualTo(SalesOrderEditOutcome.APPLIED);
  }

  // ── Review R1–R3 (CEDIT-07-REVIEW-2026-10-08) ──────────────────────────────

  @Test
  @DisplayName(
      "R1: a renewal and leaving the draft race in both orders; a token of the old epoch is never"
          + " reported renewed, and back in the draft it stays lost")
  void renewalAndLeavingTheDraftRace() throws Exception {
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));

    // The transition first: the renewal waits for the order row and sees the order out of the
    // draft.
    CountDownLatch moved = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    Future<Object> submit =
        pool.submit(
            () ->
                as(
                    actorA,
                    () ->
                        transactions.execute(
                            status -> {
                              Object result = flows.submit(orderId, actorA.id());
                              moved.countDown();
                              await(commit);
                              return result;
                            })));
    await(moved);
    Future<Object> renewal =
        pool.submit(
            () -> as(actorA, () -> leaseApi.renew(orderId, tokens(tab, token), actorA.id())));
    awaitLockWaiter();
    commit.countDown();
    assertThat(submit.get(30, TimeUnit.SECONDS)).isNotInstanceOf(RuntimeException.class);
    Object late = renewal.get(30, TimeUnit.SECONDS);
    assertThat(late).isInstanceOf(DomainException.class);
    assertThat(as(actorA, () -> flows.withdraw(orderId, "Customer changed the date", actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(renew(actorA, tab, token).lostLeaseTokens()).containsExactly(token);

    // The renewal first: the transition waits behind it and then voids the lease.
    UUID fresh = token(granted(actorA, tab, notes()));
    CountDownLatch renewed = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    Future<Object> holding =
        pool.submit(
            () ->
                as(
                    actorA,
                    () ->
                        transactions.execute(
                            status -> {
                              Object result =
                                  leaseApi.renew(orderId, tokens(tab, fresh), actorA.id());
                              renewed.countDown();
                              await(finish);
                              return result;
                            })));
    await(renewed);
    Future<Object> submitAgain =
        pool.submit(() -> as(actorA, () -> flows.submit(orderId, actorA.id())));
    awaitLockWaiter();
    finish.countDown();
    assertThat(((SalesOrderEditLeaseDtos.Renewal) holding.get(30, TimeUnit.SECONDS)).renewed())
        .hasSize(1);
    assertThat(submitAgain.get(30, TimeUnit.SECONDS)).isNotInstanceOf(RuntimeException.class);
    assertThat(leases(actorC)).isEmpty();
    assertThat(
            as(actorA, () -> flows.withdraw(orderId, "Customer changed the quantity", actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(renew(actorA, tab, fresh).lostLeaseTokens()).containsExactly(fresh);
  }

  @Test
  @DisplayName(
      "R2: void leases of an older epoch fill neither bound; real holdings of the current epoch still"
          + " do")
  void boundsIgnoreVoidLeases() {
    leaseProperties.setMaxLeasesPerSession(1);
    leaseProperties.setMaxLeasesPerResource(1);
    UUID tabA = openSession(actorA);
    UUID tabB = openSession(actorB);
    UUID old = token(granted(actorA, tabA, notes()));
    assertThat(as(actorA, () -> flows.submit(orderId, actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(as(actorA, () -> flows.withdraw(orderId, "Back to the draft", actorA.id())))
        .isNotInstanceOf(RuntimeException.class);
    assertThat(leases(actorC)).isEmpty();

    // Another session takes a field that looks free: A's void lease is not counted.
    UUID terms = token(granted(actorB, tabB, header(SalesOrderEditLeaseField.PAYMENT_TERMS)));
    // The resource bound is now really full in this epoch.
    assertThat(failureCode(acquire(actorA, tabA, notes()))).isEqualTo("EDIT_LEASE_LIMIT_REACHED");

    release(actorB, tabB, terms);
    UUID again = token(granted(actorA, tabA, notes()));
    assertThat(again).isNotEqualTo(old);
    assertThat(renew(actorA, tabA, old).lostLeaseTokens()).containsExactly(old);
    // The session bound counts the current holding: a second key is refused.
    leaseProperties.setMaxLeasesPerResource(10);
    assertThat(failureCode(acquire(actorA, tabA, header(SalesOrderEditLeaseField.DEADLINE))))
        .isEqualTo("EDIT_LEASE_LIMIT_REACHED");
  }

  @Test
  @DisplayName(
      "R3: a repeat and a renewal from a clock behind the one that granted the lease keep the token,"
          + " write consistent times and never pass the session; a later transfer still voids it")
  void clockDifferenceBetweenInstances() {
    UUID tab = openSession(actorA);
    clock.advance(Duration.ofSeconds(5));
    UUID token = token(granted(actorA, tab, notes()));
    clock.reset();

    assertThat(token(granted(actorA, tab, notes()))).isEqualTo(token);
    assertThat(renew(actorA, tab, token).renewed())
        .singleElement()
        .satisfies(lease -> assertThat(lease.leaseToken()).isEqualTo(token));
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT l.acquired_at, l.renewed_at, l.expires_at, s.expires_at AS session_expires"
                + " FROM common_infrastructure.live_edit_lease l"
                + " JOIN common_infrastructure.live_edit_session s ON s.id = l.session_id"
                + " WHERE l.token = ?",
            token);
    java.sql.Timestamp acquired = (java.sql.Timestamp) row.get("acquired_at");
    java.sql.Timestamp renewedAt = (java.sql.Timestamp) row.get("renewed_at");
    java.sql.Timestamp expires = (java.sql.Timestamp) row.get("expires_at");
    assertThat(renewedAt).isAfterOrEqualTo(acquired);
    assertThat(expires).isAfter(renewedAt);
    assertThat(expires).isBeforeOrEqualTo((java.sql.Timestamp) row.get("session_expires"));

    clock.advance(Duration.ofSeconds(200));
    UUID tabB = openSession(actorB);
    assertThat(token(granted(actorB, tabB, notes()))).isNotEqualTo(token);
    assertThat(
            failureCode(
                save(
                    actorA,
                    proof(
                        body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("A")),
                        tab,
                        token))))
        .isEqualTo("EDIT_LEASE_REQUIRED");
  }

  @Test
  @DisplayName(
      "R3 follow-up: a renewal with no consistent extension ends the period on the server; the"
          + " lost token saves nothing, is listed nowhere and blocks nobody")
  void inconsistentRenewalEndsThePeriod() {
    // Opened and granted on an instance 100 s ahead: session and lease end at t+190, the lease's
    // renewal time is t+100.
    clock.advance(Duration.ofSeconds(100));
    UUID tab = openSession(actorA);
    UUID token = token(granted(actorA, tab, notes()));
    clock.reset();
    // Renewed on this instance: the session now ends at t+90, before the lease's renewal time.
    renewSession(actorA, tab);

    SalesOrderEditLeaseDtos.Renewal renewal = renew(actorA, tab, token);

    assertThat(renewal.renewed()).isEmpty();
    assertThat(renewal.lostLeaseTokens()).containsExactly(token);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT renewed_at, released_at FROM common_infrastructure.live_edit_lease"
                + " WHERE token = ?",
            token);
    assertThat(row.get("released_at")).isNotNull();
    assertThat((java.sql.Timestamp) row.get("released_at"))
        .isAfterOrEqualTo((java.sql.Timestamp) row.get("renewed_at"));
    assertThat(leases(actorC)).isEmpty();
    // The ownership marker agrees: nothing is held on the order any more.
    LiveSse stream = subscribe(actorB);
    assertThat(lease(ready(stream))).isEqualTo("0");
    stream.close();

    int receiptsBefore = receipts();
    Object result =
        save(
            actorA,
            proof(body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("A")), tab, token));
    assertThat(failureCode(result)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(requirements(result))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.key()).isEqualTo(SalesOrderEditLeaseField.NOTES);
              assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD);
              assertThat(r.holder()).isNull();
            });
    assertThat(orderText("notes")).isNull();
    assertThat(receipts()).isEqualTo(receiptsBefore);

    UUID tabB = openSession(actorB);
    assertThat(token(granted(actorB, tabB, notes()))).isNotEqualTo(token);
    assertThat(leases(actorA))
        .singleElement()
        .satisfies(h -> assertThat(h.userId()).isEqualTo(actorB.id()));
  }

  @Test
  @DisplayName(
      "R3 follow-up: a repeated acquire with no consistent extension starts a new period with a new"
          + " token; the old token saves nothing, the new one does")
  void inconsistentRepeatStartsANewPeriod() {
    clock.advance(Duration.ofSeconds(100));
    UUID tab = openSession(actorA);
    UUID old = token(granted(actorA, tab, notes()));
    clock.reset();
    renewSession(actorA, tab);

    UUID fresh = token(granted(actorA, tab, notes()));

    assertThat(fresh).isNotEqualTo(old);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT l.acquired_at, l.renewed_at, l.expires_at, l.released_at,"
                + " s.expires_at AS session_expires"
                + " FROM common_infrastructure.live_edit_lease l"
                + " JOIN common_infrastructure.live_edit_session s ON s.id = l.session_id"
                + " WHERE l.token = ?",
            fresh);
    assertThat(row.get("released_at")).isNull();
    java.sql.Timestamp renewedAt = (java.sql.Timestamp) row.get("renewed_at");
    java.sql.Timestamp expires = (java.sql.Timestamp) row.get("expires_at");
    assertThat(renewedAt).isAfterOrEqualTo((java.sql.Timestamp) row.get("acquired_at"));
    assertThat(expires).isAfter(renewedAt);
    assertThat(expires).isBeforeOrEqualTo((java.sql.Timestamp) row.get("session_expires"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_infrastructure.live_edit_lease WHERE token = ?",
                Integer.class,
                old))
        .isZero();

    int receiptsBefore = receipts();
    Object withOld =
        save(
            actorA,
            proof(body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("A")), tab, old));
    assertThat(failureCode(withOld)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(orderText("notes")).isNull();
    assertThat(receipts()).isEqualTo(receiptsBefore);

    SalesOrderEditResult applied =
        saved(
            actorA,
            proof(body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("A")), tab, fresh));
    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isEqualTo("A");
  }

  // ── CEDIT-07-F1: a line removed since the base ────────────────────────────

  @Test
  @DisplayName(
      "DL01: A's lease ended and B removed the line; A's stale UPDATE reaches the merge and"
          + " conflicts, nothing is written and the line stays removed")
  void staleUpdateOfARemovedLineConflicts() {
    UUID tabA = openSession(actorA);
    UUID baseA = open(actorA).baseId();
    granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, l1));
    // A was away: the lease (and the session) ended.
    clock.advance(Duration.ofSeconds(100));
    removeL1ByB();
    long version = orderVersion();
    int history = historyRows();

    JsonNode problem = conflicted(actorA, staleL1Update(UUID.randomUUID(), baseA));

    assertThat(problem.path("code").asText()).isEqualTo("EDIT_CONFLICT");
    JsonNode conflict = problem.path("conflicts").get(0);
    assertThat(conflict.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_REMOVED_ON_SERVER");
    assertThat(conflict.path("choices"))
        .extracting(JsonNode::asText)
        .containsExactly("KEEP_CURRENT");
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(l1Active()).isFalse();
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isEqualTo(history);
  }

  @Test
  @DisplayName(
      "DL02: a stale REMOVE of a line already removed is a no change without any lease; the retry"
          + " answers the same receipt")
  void staleRemoveOfARemovedLineIsNoChange() {
    UUID baseA = open(actorA).baseId();
    removeL1ByB();
    long version = orderVersion();
    int history = historyRows();
    Map<String, Object> stale = withLines(body(UUID.randomUUID(), baseA), List.of(remove(l1)));

    SalesOrderEditResult first = saved(actorA, stale);
    SalesOrderEditResult again = saved(actorA, stale);

    assertThat(first.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(again.replayed()).isTrue();
    // A replay derives a fresh base from the current order (CEDIT-03 §4.3): the same receipt
    // answers, at the same version, but the base id is new each time.
    assertThat(again.resultVersion()).isEqualTo(first.resultVersion());
    assertThat(again.nextBase().orderVersion()).isEqualTo(first.nextBase().orderVersion());
    assertThat(receipts("NO_CHANGE")).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isEqualTo(history);
    assertThat(l1Active()).isFalse();
  }

  @Test
  @DisplayName(
      "DL03: beside a removed line, an active key still needs its lease; with it the save conflicts"
          + " on the removed line and writes none of its other changes")
  void removedLineBesideAnActiveKey() {
    UUID tabA = openSession(actorA);
    UUID baseA = open(actorA).baseId();
    removeL1ByB();
    long version = orderVersion();
    int history = historyRows();
    int receiptsBefore = receipts();
    Map<String, Object> mixed =
        withLines(
            body(UUID.randomUUID(), baseA, "notes", set("A")),
            List.of(update(l1, "pricing", set(pricing("GBP", "4.10")))));

    Object unproven = save(actorA, mixed);

    assertThat(requirements(unproven))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.key()).isEqualTo(SalesOrderEditLeaseField.NOTES);
              assertThat(r.lineId()).isNull();
              assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD);
            });
    assertThat(receipts()).isEqualTo(receiptsBefore);

    UUID notesToken = token(granted(actorA, tabA, notes()));
    JsonNode problem = conflicted(actorA, proof(mixed, tabA, notesToken));

    assertThat(problem.path("conflicts"))
        .anySatisfy(
            c -> {
              assertThat(c.path("lineId").asText()).isEqualTo(l1.toString());
              assertThat(c.path("reason").asText()).isEqualTo("LINE_REMOVED_ON_SERVER");
            });
    assertThat(orderText("notes")).isNull();
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isEqualTo(history);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    // A conflict keeps the lease it proved.
    assertThat(leases(actorC))
        .singleElement()
        .satisfies(h -> assertThat(h.key()).isEqualTo(SalesOrderEditLeaseField.NOTES));
  }

  @Test
  @DisplayName(
      "DL04: a stale REMOVE of a removed line beside a proven change applies only the real change"
          + " and releases the lease it used")
  void removedLineBesideAProvenChange() {
    UUID tabA = openSession(actorA);
    UUID baseA = open(actorA).baseId();
    removeL1ByB();
    UUID notesToken = token(granted(actorA, tabA, notes()));
    UUID operationId = UUID.randomUUID();

    SalesOrderEditResult result =
        saved(
            actorA,
            proof(
                withLines(body(operationId, baseA, "notes", set("A")), List.of(remove(l1))),
                tabA,
                notesToken));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isEqualTo("A");
    assertThat(
            history().stream()
                .filter(row -> operationId.equals(row.get("operation_id")))
                .map(row -> row.get("edit_key")))
        .containsExactly("notes");
    assertThat(leases(actorC)).isEmpty();
    assertThat(l1Active()).isFalse();
  }

  @Test
  @DisplayName(
      "DL05: an active line still needs its lease for UPDATE and REMOVE; the gone-line exception"
          + " is no bypass")
  void activeLinesStillNeedLeases() {
    UUID baseA = open(actorA).baseId();
    int receiptsBefore = receipts();

    Object update = save(actorA, staleL1Update(UUID.randomUUID(), baseA));
    assertThat(requirements(update))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.key()).isEqualTo(SalesOrderEditLeaseField.LINE_PRICING);
              assertThat(r.lineId()).isEqualTo(l1);
              assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD);
            });

    UUID tabB = openSession(actorB);
    granted(actorB, tabB, line(SalesOrderEditLeaseField.LINE, l1));
    Object removal = save(actorA, withLines(body(UUID.randomUUID(), baseA), List.of(remove(l1))));
    assertThat(requirements(removal))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.key()).isEqualTo(SalesOrderEditLeaseField.LINE);
              assertThat(r.lineId()).isEqualTo(l1);
              assertThat(r.reason()).isEqualTo(RequirementReason.HELD_BY_ANOTHER);
              assertThat(r.holder().userId()).isEqualTo(actorB.id());
            });
    assertThat(l1Active()).isTrue();
    assertThat(receipts()).isEqualTo(receiptsBefore);
  }

  @Test
  @DisplayName(
      "DL06: an id the base does not know is never a removed line: a made-up id still needs a"
          + " lease, a line added since the base is refused by the base check, nothing is written")
  void unknownLinesKeepTheirChecks() {
    UUID tabA = openSession(actorA);
    UUID baseA = open(actorA).baseId();
    UUID madeUp = UUID.randomUUID();

    Object fabricated =
        save(
            actorA,
            withLines(
                body(UUID.randomUUID(), baseA),
                List.of(update(madeUp, "pricing", set(pricing("GBP", "4.10"))))));
    assertThat(requirements(fabricated))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.lineId()).isEqualTo(madeUp);
              assertThat(r.reason()).isEqualTo(RequirementReason.NOT_HELD);
              assertThat(r.holder()).isNull();
            });
    assertThat(
            failureCode(acquire(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, madeUp))))
        .isEqualTo("EDIT_LEASE_KEY_INVALID");

    // B adds a line after A's base: it is active and leasable, but A's base does not know it. A
    // product of its own, so the line is not L1's distribution again.
    UUID p3 = UUID.randomUUID();
    SalesOrderEditResult added =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), open(actorB).baseId()),
                List.of(add(UUID.randomUUID(), p3, "quantity", set(quantity("200", "M"))))));
    UUID l3 = added.lineIds().getFirst().lineId();
    UUID token = token(granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, l3)));
    int receiptsBefore = receipts();
    long version = orderVersion();

    Object notInBase =
        save(
            actorA,
            proof(
                withLines(
                    body(UUID.randomUUID(), baseA),
                    List.of(update(l3, "pricing", set(pricing("GBP", "4.10"))))),
                tabA,
                token));

    assertThat(failureCode(notInBase)).isEqualTo("LINE_NOT_IN_BASE");
    assertThat(receipts()).isEqualTo(receiptsBefore);
    assertThat(orderVersion()).isEqualTo(version);
  }

  @Test
  @DisplayName(
      "DL07: the removed line's old token is unexpected and records nothing; the same operation"
          + " without it reaches the conflict")
  void oldTokenOfARemovedLine() {
    UUID tabA = openSession(actorA);
    UUID baseA = open(actorA).baseId();
    UUID old = token(granted(actorA, tabA, line(SalesOrderEditLeaseField.LINE_PRICING, l1)));
    release(actorA, tabA, old);
    removeL1ByB();
    int receiptsBefore = receipts();
    Map<String, Object> stale = staleL1Update(UUID.randomUUID(), baseA);

    Object withOld = save(actorA, proof(stale, tabA, old));

    assertThat(failureCode(withOld)).isEqualTo("EDIT_LEASE_TOKEN_UNEXPECTED");
    assertThat(receipts()).isEqualTo(receiptsBefore);

    JsonNode problem = conflicted(actorA, stale);
    assertThat(problem.path("conflicts").get(0).path("reason").asText())
        .isEqualTo("LINE_REMOVED_ON_SERVER");
    assertThat(receipts("CONFLICT")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "DL08: a removal that commits while the stale save waits for the order row is seen: the"
          + " active lines are read after that lock")
  void removalWhileTheSaveWaits() throws Exception {
    UUID baseA = open(actorA).baseId();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch unlock = new CountDownLatch(1);
    Future<Object> remover = pool.submit(() -> removeLineHoldingOrder(l1, locked, unlock));
    await(locked);
    Future<Object> stale = pool.submit(() -> save(actorA, staleL1Update(UUID.randomUUID(), baseA)));
    awaitLockWaiters(1);

    unlock.countDown();
    remover.get(30, TimeUnit.SECONDS);
    Object answer = stale.get(30, TimeUnit.SECONDS);

    assertThat(answer).isInstanceOf(SalesOrderEditConflictException.class);
    assertThat(
            ((SalesOrderEditConflictException) answer)
                .body()
                .path("conflicts")
                .get(0)
                .path("reason")
                .asText())
        .isEqualTo("LINE_REMOVED_ON_SERVER");
    assertThat(l1Active()).isFalse();
  }

  @Test
  @DisplayName(
      "DL08: a stale save that takes the order row before the removal decides on the line as it is"
          + " then: still active, so it needs its lease; the removal follows")
  void staleSaveBeforeTheRemoval() throws Exception {
    UUID baseA = open(actorA).baseId();
    UUID tabB = openSession(actorB);
    UUID whole = token(granted(actorB, tabB, line(SalesOrderEditLeaseField.LINE, l1)));
    Map<String, Object> removal =
        proof(
            withLines(body(UUID.randomUUID(), open(actorB).baseId()), List.of(remove(l1))),
            tabB,
            whole);
    UUID operationId = UUID.randomUUID();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch unlock = new CountDownLatch(1);
    Future<Object> holder = pool.submit(() -> holdOrderRow(locked, unlock));
    await(locked);
    Future<Object> stale = pool.submit(() -> save(actorA, staleL1Update(operationId, baseA)));
    awaitLockWaiters(1);
    Future<Object> removing = pool.submit(() -> save(actorB, removal));
    awaitLockWaiters(2);

    unlock.countDown();
    holder.get(30, TimeUnit.SECONDS);

    assertThat(requirements(stale.get(30, TimeUnit.SECONDS)))
        .singleElement()
        .satisfies(r -> assertThat(r.reason()).isEqualTo(RequirementReason.HELD_BY_ANOTHER));
    assertThat(((SalesOrderEditResult) removing.get(30, TimeUnit.SECONDS)).outcome())
        .isEqualTo(SalesOrderEditOutcome.APPLIED);
    // The same operation afterwards sees the removal.
    assertThat(
            conflicted(actorA, staleL1Update(operationId, baseA))
                .path("conflicts")
                .get(0)
                .path("reason")
                .asText())
        .isEqualTo("LINE_REMOVED_ON_SERVER");
  }

  @Test
  @DisplayName(
      "DL09: a replayed conflict of a removed line needs no lease and releases nobody's; a stale"
          + " REMOVE of it needs no proof")
  void replayAroundARemovedLine() {
    UUID baseA = open(actorA).baseId();
    UUID baseC = open(actorC).baseId();
    removeL1ByB();
    Map<String, Object> stale = staleL1Update(UUID.randomUUID(), baseA);

    JsonNode first = conflicted(actorA, stale);
    assertThat(first.path("conflicts").get(0).path("reason").asText())
        .isEqualTo("LINE_REMOVED_ON_SERVER");

    UUID tabB = openSession(actorB);
    granted(actorB, tabB, notes());
    JsonNode again = conflicted(actorA, stale);

    assertThat(again.path("conflicts")).isEqualTo(first.path("conflicts"));
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    assertThat(leases(actorC))
        .singleElement()
        .satisfies(h -> assertThat(h.userId()).isEqualTo(actorB.id()));
    assertThat(
            saved(actorC, withLines(body(UUID.randomUUID(), baseC), List.of(remove(l1)))).outcome())
        .isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
  }

  // ── L18–L19: signal and bounds ────────────────────────────────────────────

  @Test
  @DisplayName(
      "L18 (CE-18): acquire, release and expiry change the stream's lease marker only; renewal is"
          + " quiet; a reconnect starts from the current marker")
  void streamSignalsLeases() {
    UUID tab = openSession(actorA);
    LiveSse stream = subscribe(actorB);
    LiveSse.Frame first = ready(stream);
    String version = first.revision();
    assertThat(lease(first)).isEqualTo("0");

    UUID token = token(granted(actorA, tab, notes()));
    LiveSse.Frame acquired = stream.nextEvent(WAIT);
    assertThat(acquired.event()).isEqualTo("invalidated");
    assertThat(acquired.revision()).isEqualTo(version);
    assertThat(lease(acquired)).startsWith("l");
    assertThat(acquired.body().toString())
        .doesNotContain(token.toString())
        .doesNotContain(actorA.id().toString());

    renew(actorA, tab, token);
    expectQuiet(stream);

    release(actorA, tab, token);
    assertThat(lease(stream.nextEvent(WAIT))).isEqualTo("0");

    granted(actorA, tab, notes());
    assertThat(lease(stream.nextEvent(WAIT))).isNotEqualTo("0");
    jdbc.update(
        "UPDATE common_infrastructure.live_edit_lease SET acquired_at = acquired_at - interval '10 minutes',"
            + " renewed_at = renewed_at - interval '10 minutes', expires_at = now() - interval '1 minute'"
            + " WHERE resource_id = ? AND released_at IS NULL",
        orderId);
    assertThat(lease(stream.nextEvent(WAIT))).isEqualTo("0");

    stream.close();
    granted(actorA, tab, header(SalesOrderEditLeaseField.DEADLINE));
    assertThat(lease(ready(subscribe(actorB)))).startsWith("l");
  }

  @Test
  @DisplayName(
      "L19: bounds refuse as a whole; a repeated acquire keeps its token; unknown, null and"
          + " duration fields are refused on the wire")
  void boundsAndWireShape() throws Exception {
    UUID tab = openSession(actorA);
    Grant first = granted(actorA, tab, notes());
    Grant repeat = granted(actorA, tab, notes());
    assertThat(token(repeat)).isEqualTo(token(first));
    assertThat(repeat.leases().getFirst().acquiredAt())
        .isEqualTo(first.leases().getFirst().acquiredAt());

    leaseProperties.setMaxLeasesPerSession(2);
    Object tooMany =
        acquire(
            actorA,
            tab,
            header(SalesOrderEditLeaseField.DEADLINE),
            header(SalesOrderEditLeaseField.PAYMENT_TERMS));
    assertThat(failureCode(tooMany)).isEqualTo("EDIT_LEASE_LIMIT_REACHED");
    assertThat(leases(actorA)).hasSize(1);

    String path = leasesPath();
    assertThat(
            http("POST", path, actorA, "{\"editSessionId\":null,\"keys\":[{\"key\":\"notes\"}]}")
                .status())
        .isEqualTo(422);
    assertThat(
            http(
                    "POST",
                    path,
                    actorA,
                    "{\"editSessionId\":\""
                        + tab
                        + "\",\"keys\":[{\"key\":\"notes\",\"part\":\"x\"}]}")
                .status())
        .isEqualTo(400);
    assertThat(
            http(
                    "POST",
                    path + "/renew",
                    actorA,
                    "{\"editSessionId\":\""
                        + tab
                        + "\",\"leaseTokens\":[\""
                        + token(first)
                        + "\"],\"expiresAt\":\"2099-01-01T00:00:00Z\"}")
                .status())
        .isEqualTo(400);
    assertThat(
            http(
                    "POST",
                    path + "/renew",
                    actorA,
                    "{\"editSessionId\":\"" + tab + "\",\"leaseTokens\":[]}")
                .status())
        .isEqualTo(422);

    Answer listed = http("GET", path, actorB, null);
    assertThat(listed.status()).isEqualTo(200);
    assertThat(listed.body().toString())
        .doesNotContain(token(first).toString())
        .doesNotContain(tab.toString());
    assertThat(listed.body().path("data").path("policy").path("mode").asText())
        .isEqualTo("ENFORCED");
    assertThat(listed.body().path("data").path("policy").path("renewAfterSeconds").asLong())
        .isEqualTo(30);
    Answer own = http("GET", path, actorA, null);
    assertThat(own.body().path("data").path("leases").get(0).path("editSessionId").asText())
        .isEqualTo(tab.toString());
    assertThat(
            http(
                    "POST",
                    path + "/release",
                    actorA,
                    "{\"editSessionId\":\""
                        + tab
                        + "\",\"leaseTokens\":[\""
                        + token(first)
                        + "\"]}")
                .status())
        .isEqualTo(204);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private UUID openSession(Actor actor) {
    Object result = as(actor, () -> sessions.open(orderId, actor.id()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
    return ((com.fabricmanagement.sales.salesorder.dto.SalesOrderEditSessionDto) result)
        .editSessionId();
  }

  private void renewSession(Actor actor, UUID session) {
    Object result = as(actor, () -> sessions.renew(orderId, session, actor.id()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
  }

  private static SalesOrderEditLeaseKey notes() {
    return header(SalesOrderEditLeaseField.NOTES);
  }

  private static SalesOrderEditLeaseKey header(SalesOrderEditLeaseField field) {
    return new SalesOrderEditLeaseKey(field, null);
  }

  private static SalesOrderEditLeaseKey line(SalesOrderEditLeaseField field, UUID lineId) {
    return new SalesOrderEditLeaseKey(field, lineId);
  }

  private static SalesOrderEditLeaseDtos.AcquireRequest request(
      UUID session, SalesOrderEditLeaseKey... keys) {
    return new SalesOrderEditLeaseDtos.AcquireRequest(session, List.of(keys));
  }

  private static SalesOrderEditLeaseDtos.TokensRequest tokens(UUID session, UUID... tokens) {
    return new SalesOrderEditLeaseDtos.TokensRequest(session, List.of(tokens));
  }

  private Object acquire(Actor actor, UUID session, SalesOrderEditLeaseKey... keys) {
    return as(actor, () -> leaseApi.acquire(orderId, request(session, keys), actor.id()));
  }

  private Grant granted(Actor actor, UUID session, SalesOrderEditLeaseKey... keys) {
    Object result = acquire(actor, session, keys);
    if (result instanceof RuntimeException failure) {
      throw new AssertionError("Expected a grant but got " + failure, failure);
    }
    return (Grant) result;
  }

  private SalesOrderEditLeaseDtos.Renewal renew(Actor actor, UUID session, UUID... leaseTokens) {
    Object result =
        as(actor, () -> leaseApi.renew(orderId, tokens(session, leaseTokens), actor.id()));
    if (result instanceof RuntimeException failure) {
      throw new AssertionError("Expected a renewal but got " + failure, failure);
    }
    return (SalesOrderEditLeaseDtos.Renewal) result;
  }

  private void release(Actor actor, UUID session, UUID... leaseTokens) {
    Object result =
        as(
            actor,
            () -> run(() -> leaseApi.release(orderId, tokens(session, leaseTokens), actor.id())));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
  }

  private List<Holder> leases(Actor actor) {
    Object result = as(actor, () -> leaseApi.list(orderId, actor.id()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
    return ((SalesOrderEditLeaseDtos.Leases) result).leases();
  }

  private static UUID token(Grant grant) {
    assertThat(grant.leases()).hasSize(1);
    return grant.leases().getFirst().leaseToken();
  }

  private static UUID token(Grant grant, SalesOrderEditLeaseField field) {
    return grant.leases().stream()
        .filter(lease -> lease.key() == field)
        .findFirst()
        .orElseThrow()
        .leaseToken();
  }

  /** The save body with this tab's proof. */
  private static Map<String, Object> proof(
      Map<String, Object> body, UUID session, UUID... leaseTokens) {
    Map<String, Object> withProof = new LinkedHashMap<>(body);
    withProof.put("editSessionId", session);
    if (leaseTokens.length > 0) {
      withProof.put("leaseTokens", List.of(leaseTokens));
    }
    return withProof;
  }

  @SuppressWarnings("unchecked")
  private static List<Holder> holders(Object failure) {
    assertThat(failure).isInstanceOf(DomainException.class);
    return (List<Holder>) ((DomainException) failure).getDetails().get("holders");
  }

  @SuppressWarnings("unchecked")
  private static List<Requirement> requirements(Object failure) {
    assertThat(failure).isInstanceOf(DomainException.class);
    assertThat(((DomainException) failure).getErrorCode()).isEqualTo("EDIT_LEASE_REQUIRED");
    return (List<Requirement>) ((DomainException) failure).getDetails().get("leases");
  }

  private Object setRequestedDate(Actor actor) {
    return as(
        actor,
        () ->
            parties.setRequestedDate(
                orderId,
                new OrderPartyDtos.SetRequestedDateRequest(orderVersion(), null),
                actor.id()));
  }

  private Object correctL1(Actor actor) {
    long version = lineVersion(l1);
    return as(
        actor,
        () ->
            productCorrections.correct(
                orderId,
                new FulfilmentDtos.CorrectProduct(
                    p1,
                    UUID.randomUUID(),
                    List.of(new FulfilmentDtos.LineVersion(l1, version)),
                    "Wrong article chosen"),
                actor.id()));
  }

  /** B removes L1 with its whole-line lease; a base opened before still has the line. */
  private void removeL1ByB() {
    UUID tabB = openSession(actorB);
    UUID whole = token(granted(actorB, tabB, line(SalesOrderEditLeaseField.LINE, l1)));
    saved(
        actorB,
        proof(
            withLines(body(UUID.randomUUID(), open(actorB).baseId()), List.of(remove(l1))),
            tabB,
            whole));
    assertThat(l1Active()).isFalse();
  }

  /** A's pending pricing change of L1, against its own older base, without any proof. */
  private Map<String, Object> staleL1Update(UUID operationId, UUID baseId) {
    return withLines(
        body(operationId, baseId), List.of(update(l1, "pricing", set(pricing("GBP", "4.10")))));
  }

  private boolean l1Active() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT is_active FROM sales_ord.sales_order_line WHERE id = ?", Boolean.class, l1));
  }

  /**
   * A line writer of another connection: takes the order row, then removes the line and moves the
   * order version, committing on {@code unlock}.
   */
  private Object removeLineHoldingOrder(UUID lineId, CountDownLatch locked, CountDownLatch unlock)
      throws Exception {
    try (Connection owner = ownerConnection()) {
      owner.setAutoCommit(false);
      try (var lock =
          owner.prepareStatement("SELECT id FROM sales_ord.sales_order WHERE id = ? FOR UPDATE")) {
        lock.setObject(1, orderId);
        lock.executeQuery().close();
      }
      locked.countDown();
      await(unlock);
      try (var removal =
          owner.prepareStatement(
              "UPDATE sales_ord.sales_order_line SET is_active = false WHERE id = ?")) {
        removal.setObject(1, lineId);
        removal.executeUpdate();
      }
      try (var version =
          owner.prepareStatement(
              "UPDATE sales_ord.sales_order SET version = version + 1 WHERE id = ?")) {
        version.setObject(1, orderId);
        version.executeUpdate();
      }
      owner.commit();
      return Boolean.TRUE;
    }
  }

  /** Holds the order row like a writer in the middle of its transaction. */
  private Object holdOrderRow(CountDownLatch locked, CountDownLatch unlock) throws Exception {
    return holdRow(
        "SELECT id FROM sales_ord.sales_order WHERE id = ? FOR UPDATE", orderId, locked, unlock);
  }

  /** Holds one line row: a save that passed its lease check waits for its line locks. */
  private Object holdLineRow(UUID lineId, CountDownLatch locked, CountDownLatch unlock)
      throws Exception {
    return holdRow(
        "SELECT id FROM sales_ord.sales_order_line WHERE id = ? FOR UPDATE",
        lineId,
        locked,
        unlock);
  }

  /** A row lock held by another connection (the owner role) until {@code unlock}. */
  private static Object holdRow(String sql, UUID id, CountDownLatch locked, CountDownLatch unlock)
      throws Exception {
    try (Connection owner = ownerConnection()) {
      owner.setAutoCommit(false);
      try (var lock = owner.prepareStatement(sql)) {
        lock.setObject(1, id);
        lock.executeQuery().close();
      }
      locked.countDown();
      await(unlock);
      owner.commit();
      return Boolean.TRUE;
    }
  }

  private void awaitLockWaiters(int count) {
    awaitCondition(
        Duration.ofSeconds(20),
        () -> {
          Integer waiting =
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                      + " AND wait_event_type = 'Lock'",
                  Integer.class);
          return waiting != null && waiting >= count;
        });
  }

  private static long count(UUID tenant, String sql) throws Exception {
    try (Connection connection = appConnection(tenant);
        var statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static String lease(LiveSse.Frame frame) {
    return frame.body().path("leaseRevision").asText();
  }

  private static Boolean run(Runnable step) {
    step.run();
    return Boolean.TRUE;
  }

  private String leasesPath() {
    return "/api/v1/sales/orders/" + orderId + "/edit-leases";
  }

  /** A legacy full replace at the current version: header as fixed, lines exactly as stored. */
  private UpdateSalesOrderRequest legacyRequest(String notes) {
    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(orderVersion());
    request.setOrderDate(ORDER_DATE);
    request.setDeliveryTerm(DeliveryTerm.FCA);
    request.setDeliveryPlace("Leeds");
    request.setIncotermsVersion(IncotermsVersion.INCOTERMS_2020);
    request.setDeliveryTermStatus(DeliveryTermStatus.PROPOSED);
    request.setPaymentTerms("30 days");
    request.setContactName("Jane Hill");
    request.setContactEmail("jane@example.com");
    request.setNotes(notes);
    request.setLines(
        new ArrayList<>(List.of(lineRequest(loadLine(l1)), lineRequest(loadLine(l2)))));
    return request;
  }

  private static UpdateSalesOrderLineRequest lineRequest(SalesOrderLine line) {
    return UpdateSalesOrderLineRequest.builder()
        .id(line.getId())
        .productId(line.getProductId())
        .productDesc(line.getProductDesc())
        .colorId(line.getColorId())
        .finishedWidth(line.getFinishedWidth())
        .finishedWidthUnit(line.getFinishedWidthUnit())
        .requestedDeliveryDate(line.getRequestedDeliveryDate())
        .singleLotRequired(line.isSingleLotRequired())
        .requestedQty(line.getRequestedQty())
        .unit(line.getUnit())
        .unitPrice(line.getUnitPriceAmount())
        .currency(line.getCurrency())
        .discountAmount(line.getDiscountAmountValue())
        .taxAmount(line.getTaxAmountValue())
        .toleranceUpPct(line.getToleranceUpPct())
        .toleranceDownPct(line.getToleranceDownPct())
        .moduleType(line.getModuleType())
        .moduleSpecs(line.getModuleSpecs())
        .build();
  }

  private SalesOrderLine loadLine(UUID lineId) {
    Object line = as(actorA, () -> lines.findByTenantIdAndId(tenantId, lineId).orElseThrow());
    if (line instanceof RuntimeException failure) {
      throw failure;
    }
    return (SalesOrderLine) line;
  }

  private record Answer(int status, JsonNode body) {}

  private Answer http(String method, String path, Actor actor, String json)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token(actor))
            .header("Content-Type", "application/json")
            .method(
                method,
                json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    String text = response.body();
    JsonNode body = text == null || text.isBlank() ? null : objectMapper.readTree(text);
    return new Answer(response.statusCode(), body);
  }
}
