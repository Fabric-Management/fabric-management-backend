package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.DefaultSource;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveriesView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryContentInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryCorrection;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.VerifyDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.DeliveryTermInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyInput;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CONSIGNEE-1 S2 on a real database: the verification columns map, a save by a user without the
 * permission waits, verifying confirms the delivery and the partner and address card it uses, a
 * stale version verifies nothing, and rows that predate the rule are verified.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class OrderDeliveryVerificationIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("delivery_verification_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private TenantRepository tenants;
  @Autowired private TradingPartnerRegistryRepository registries;
  @Autowired private TradingPartnerRepository partners;
  @Autowired private AddressRepository addresses;
  @Autowired private SalesOrderService orderService;
  @Autowired private OrderDeliveryService deliveries;
  @Autowired private OrderDeliveryRepository deliveryRepository;
  @Autowired private JdbcTemplate jdbc;

  /** Access scopes are covered elsewhere; a verifier needs none of them. */
  @MockitoBean private SalesOrderAccessPolicy accessPolicy;

  /** The real policy; a test makes the current user a holder of sales:verify-delivery. */
  @MockitoSpyBean private DeliveryVerificationPolicy verificationPolicy;

  private final UUID user = UUID.randomUUID();
  private final UUID verifier = UUID.randomUUID();
  private UUID tenantId;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    when(accessPolicy.canRead(any(), any(), any())).thenReturn(true);
    when(accessPolicy.canWrite(any(), any(), any())).thenReturn(true);
    tenantId = newTenant();
    customerId = inTenant(tenantId, this::newCustomer);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  // ─── a save, and the verify step ──────────────────────────────────────────

  @Test
  void aDeliverySavedWithoutThePermissionWaitsAndVerifyingConfirmsItsPartnerAndCardToo() {
    UUID partner = inTenant(tenantId, () -> newPartner(Verification.unverified()));
    UUID card = inTenant(tenantId, () -> newCard(Verification.unverified()));
    UUID orderId = newOrder("PO-V1");

    DeliveriesView saved =
        save(orderId, content(partnerInput(partner), addressFromCard(card), "Gate 3"));
    DeliveryView delivery = saved.deliveries().get(0);

    assertThat(delivery.verificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(delivery.note()).isEqualTo("Gate 3");
    assertThat(delivery.shipToVerifiedAtCopy()).isFalse();
    assertThat(deliveryStatus(delivery.id())).isEqualTo("UNVERIFIED");

    // The verifier is in planning: no sales scope at all.
    when(accessPolicy.canRead(any(), any(), any())).thenReturn(false);
    when(accessPolicy.canWrite(any(), any(), any())).thenReturn(false);
    DeliveriesView verified =
        inTenant(
            tenantId,
            () ->
                deliveries.verify(
                    orderId,
                    delivery.id(),
                    new VerifyDeliveryRequest(saved.orderVersion(), null),
                    verifier));

    DeliveryView after = verified.deliveries().get(0);
    assertThat(after.verificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(after.verifiedBy()).isEqualTo(verifier);
    assertThat(after.verifiedAt()).isNotNull();
    assertThat(deliveryStatus(delivery.id())).isEqualTo("VERIFIED");
    assertThat(statusOf("common_company.common_trading_partner", partner)).isEqualTo("VERIFIED");
    assertThat(statusOf("common_communication.common_address", card)).isEqualTo("VERIFIED");
    assertThat(verifiedByOf("common_company.common_trading_partner", partner)).isEqualTo(verifier);
    assertThat(verifiedByOf("common_communication.common_address", card)).isEqualTo(verifier);
    assertThat(verified.orderVersion())
        .as("a plain verification changes no order content")
        .isEqualTo(saved.orderVersion())
        .isEqualTo(versionInDatabase(orderId));
  }

  @Test
  void aStaleVersionVerifiesNothing() {
    UUID partner = inTenant(tenantId, () -> newPartner(Verification.unverified()));
    UUID card = inTenant(tenantId, () -> newCard(Verification.unverified()));
    UUID orderId = newOrder("PO-V2");
    DeliveriesView first =
        save(orderId, content(partnerInput(partner), addressFromCard(card), null));
    UUID deliveryId = first.deliveries().get(0).id();
    // Sales changes the order after the verifier looked at it.
    DeliveriesView second =
        saveAt(orderId, first.orderVersion(), content(customerInput(), typedAddress(), null));
    assertThat(second.orderVersion()).isGreaterThan(first.orderVersion());

    Object result =
        attempt(
            () ->
                deliveries.verify(
                    orderId,
                    deliveryId,
                    new VerifyDeliveryRequest(first.orderVersion(), null),
                    verifier));

    assertThat(result).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(deliveryStatus(deliveryId)).isEqualTo("UNVERIFIED");
    assertThat(statusOf("common_company.common_trading_partner", partner)).isEqualTo("UNVERIFIED");
    assertThat(statusOf("common_communication.common_address", card)).isEqualTo("UNVERIFIED");
  }

  @Test
  void aNoteOnlyDeliveryIsRefusedUnlessTheCustomerCollects() {
    UUID orderId = newOrder("PO-V3");
    DeliveriesView saved =
        save(orderId, content(customerInput(), null, "The Hackney workshop - Oliver to confirm"));
    UUID deliveryId = saved.deliveries().get(0).id();

    Object refused =
        attempt(
            () ->
                deliveries.verify(
                    orderId,
                    deliveryId,
                    new VerifyDeliveryRequest(saved.orderVersion(), null),
                    verifier));

    assertThat(refused)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_NOTE_ONLY"));
    assertThat(deliveryStatus(deliveryId)).isEqualTo("UNVERIFIED");

    DeliveriesView collects =
        saveAs(
            orderId,
            deliveryId,
            saved.orderVersion(),
            new DeliveryContentInput(
                customerInput(),
                null,
                DefaultSource.OVERRIDE,
                new DeliveryTermInput(DeliveryTerm.EXW, "Mill gate", null, null, null),
                null,
                null,
                null,
                false,
                "Customer collects"));
    DeliveriesView verified =
        inTenant(
            tenantId,
            () ->
                deliveries.verify(
                    orderId,
                    deliveryId,
                    new VerifyDeliveryRequest(collects.orderVersion(), null),
                    verifier));

    DeliveryView after = verified.deliveries().get(0);
    assertThat(after.verificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(after.shipTo()).isNull();
    assertThat(after.note()).isEqualTo("Customer collects");
  }

  @Test
  void aCorrectionReplacesTheAddressMovesTheOrderVersionAndConfirmsTheDelivery() {
    UUID orderId = newOrder("PO-V4");
    DeliveriesView saved = save(orderId, content(customerInput(), typedAddress(), null));
    UUID deliveryId = saved.deliveries().get(0).id();

    DeliveriesView verified =
        inTenant(
            tenantId,
            () ->
                deliveries.verify(
                    orderId,
                    deliveryId,
                    new VerifyDeliveryRequest(
                        saved.orderVersion(),
                        new DeliveryCorrection(
                            customerInput(),
                            new AddressInput("9 New St", null, "York", null, null, "GB", null),
                            "address corrected by planning")),
                    verifier));

    DeliveryView after = verified.deliveries().get(0);
    assertThat(after.shipTo().line1()).isEqualTo("9 New St");
    assertThat(after.note()).isEqualTo("address corrected by planning");
    assertThat(after.verificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(verified.orderVersion())
        .isGreaterThan(saved.orderVersion())
        .isEqualTo(versionInDatabase(orderId));
    assertThat(
            jdbc.queryForObject(
                "SELECT ship_to_line1 FROM sales_ord.order_delivery WHERE id = ?",
                String.class,
                deliveryId))
        .isEqualTo("9 New St");
  }

  @Test
  void whatAVerifierSavesCompleteIsVerifiedAtOnceAndANoteOnlyOneIsNot() {
    doReturn(true).when(verificationPolicy).actorMayVerify();
    UUID orderId = newOrder("PO-V5");

    DeliveriesView complete = save(orderId, content(customerInput(), typedAddress(), null));
    DeliveriesView withNote =
        saveAt(
            orderId,
            complete.orderVersion(),
            content(customerInput(), null, "Hackney workshop, Oliver confirms"));

    DeliveryView first = complete.deliveries().get(0);
    assertThat(first.verificationStatus()).isEqualTo(DeliveryVerificationStatus.VERIFIED);
    assertThat(first.verifiedBy()).isEqualTo(user);
    DeliveryView second = withNote.deliveries().get(1);
    assertThat(second.verificationStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(deliveryStatus(second.id())).isEqualTo("UNVERIFIED");
  }

  // ─── the verification work (C8) ───────────────────────────────────────────

  @Test
  void theVerificationWorkOpensOncePerOrderAndClosesWhenTheLastDeliveryIsVerified() {
    UUID orderId = newOrder("PO-W1");

    DeliveriesView first = save(orderId, content(customerInput(), typedAddress(), null));
    DeliveriesView both = save(orderId, content(customerInput(), typedAddress(), "Dock 4"));

    assertThat(workRows(orderId)).as("one piece of work, not one per delivery").isEqualTo(1);
    assertThat(workDepartment(orderId)).isEqualTo("PLANNING");
    assertThat(workClosed(orderId)).isFalse();
    assertThat(workEvents(orderId)).containsExactly("ROUTED");

    verifyDelivery(orderId, first.deliveries().get(0).id());
    assertThat(workClosed(orderId)).as("the second delivery still waits").isFalse();

    verifyDelivery(orderId, both.deliveries().get(1).id());
    assertThat(workClosed(orderId)).isTrue();
    assertThat(workEvents(orderId)).containsExactly("ROUTED", "CLOSED");
  }

  @Test
  void editingAVerifiedDeliveryOpensTheSameWorkAgain() {
    UUID orderId = newOrder("PO-W2");
    DeliveriesView saved = save(orderId, content(customerInput(), typedAddress(), null));
    UUID deliveryId = saved.deliveries().get(0).id();
    verifyDelivery(orderId, deliveryId);
    assertThat(workClosed(orderId)).isTrue();

    saveAs(
        orderId,
        deliveryId,
        versionInDatabase(orderId),
        content(customerInput(), typedAddress(), "Ring the bell"));

    assertThat(workRows(orderId)).isEqualTo(1);
    assertThat(workClosed(orderId)).isFalse();
    assertThat(workEvents(orderId)).containsExactly("ROUTED", "CLOSED", "ROUTED");
  }

  @Test
  void whatAVerifierSavesCompleteOpensNoWorkAtAll() {
    doReturn(true).when(verificationPolicy).actorMayVerify();
    UUID orderId = newOrder("PO-W3");

    save(orderId, content(customerInput(), typedAddress(), null));

    assertThat(workRows(orderId)).isZero();
  }

  @Test
  void removingTheOnlyWaitingDeliveryClosesTheWork() {
    UUID orderId = newOrder("PO-W4");
    DeliveriesView saved = save(orderId, content(customerInput(), typedAddress(), null));

    inTenant(
        tenantId,
        () ->
            deliveries.delete(
                orderId, saved.deliveries().get(0).id(), versionInDatabase(orderId), user));

    assertThat(workClosed(orderId)).isTrue();
  }

  // ─── the address cards a consignee was shipped to (C11) ───────────────────

  @Test
  void theMostUsedCardComesFirstAndALaterUseBreaksATie() {
    UUID partner = inTenant(tenantId, () -> newPartner(Verification.unverified()));
    UUID popular = inTenant(tenantId, () -> newCard(Verification.unverified()));
    UUID recent = inTenant(tenantId, () -> newCard(Verification.unverified()));
    UUID unused = inTenant(tenantId, () -> newCard(Verification.unverified()));

    shipTo(partner, popular);
    shipTo(partner, popular);
    shipTo(partner, recent);
    assertThat(cardsUsedFor(partner)).containsExactly(popular, recent).doesNotContain(unused);

    shipTo(partner, recent);
    assertThat(cardsUsedFor(partner))
        .as("equally used: the one used last first")
        .containsExactly(recent, popular);
  }

  @Test
  void aDeliveryToTheCustomerItselfCountsForTheCustomerAndNotForOthers() {
    UUID card = inTenant(tenantId, () -> newCard(Verification.unverified()));
    UUID other = inTenant(tenantId, () -> newPartner(Verification.unverified()));

    save(newOrder("PO-S1"), content(customerInput(), addressFromCard(card), null));

    assertThat(cardsUsedFor(customerId)).containsExactly(card);
    assertThat(cardsUsedFor(other)).isEmpty();
  }

  @Test
  void deliveriesWithATypedAddressLeaveNoCardToSuggest() {
    UUID partner = inTenant(tenantId, () -> newPartner(Verification.unverified()));

    save(newOrder("PO-S2"), content(partnerInput(partner), typedAddress(), null));

    assertThat(cardsUsedFor(partner)).isEmpty();
  }

  // ─── rows that predate the rule ───────────────────────────────────────────

  @Test
  void rowsInsertedWithoutTheColumnsAreVerifiedAsExistingRowsMigrate() {
    String addressUid = "ADDR-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_communication.common_address"
            + " (tenant_id, uid, street_address, city, country, address_type)"
            + " VALUES (?, ?, '1 Old St', 'Leeds', 'United Kingdom', 'SHIPPING')",
        tenantId,
        addressUid);
    UUID registryId = inTenant(tenantId, this::newRegistry);
    String partnerUid = "TP-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_company.common_trading_partner"
            + " (tenant_id, uid, registry_id, partner_type) VALUES (?, ?, ?, 'SUPPLIER')",
        tenantId,
        partnerUid,
        registryId);

    assertThat(
            jdbc.queryForObject(
                "SELECT verification_status FROM common_communication.common_address"
                    + " WHERE uid = ?",
                String.class,
                addressUid))
        .isEqualTo("VERIFIED");
    assertThat(
            jdbc.queryForObject(
                "SELECT verification_status FROM common_company.common_trading_partner"
                    + " WHERE uid = ?",
                String.class,
                partnerUid))
        .isEqualTo("VERIFIED");
  }

  @Test
  void aDeliveryNotKnownToBeVerifiedIsNotAndTheStatusIsLimitedToTwoValues() {
    assertThat(columnDefault("sales_ord", "order_delivery")).contains("UNVERIFIED");
    assertThat(columnDefault("common_communication", "common_address")).contains("VERIFIED");
    assertThat(columnDefault("common_company", "common_trading_partner")).contains("VERIFIED");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO common_communication.common_address (tenant_id, uid,"
                        + " street_address, city, country, address_type, verification_status)"
                        + " VALUES (?, ?, 'x', 'x', 'x', 'SHIPPING', 'MAYBE')",
                    tenantId,
                    "ADDR-" + UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private static PartyInput customerInput() {
    return new PartyInput(PartyMode.CUSTOMER, null, null);
  }

  private static PartyInput partnerInput(UUID partner) {
    return new PartyInput(PartyMode.PARTNER, partner, null);
  }

  private static AddressInput typedAddress() {
    return new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", null);
  }

  private static AddressInput addressFromCard(UUID card) {
    return new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", card);
  }

  private static DeliveryContentInput content(
      PartyInput consignee, AddressInput shipTo, String note) {
    return new DeliveryContentInput(consignee, shipTo, null, null, null, null, null, false, note);
  }

  private UUID newOrder(String reference) {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(customerId);
    request.setOrderDate(LocalDate.of(2026, 10, 2));
    request.setCustomerReference(reference);
    SalesOrderDto created = inTenant(tenantId, () -> orderService.createOrder(request));
    return created.getId();
  }

  private DeliveriesView save(UUID orderId, DeliveryContentInput input) {
    return saveAt(orderId, versionInDatabase(orderId), input);
  }

  private DeliveriesView saveAt(UUID orderId, long version, DeliveryContentInput input) {
    return inTenant(
        tenantId, () -> deliveries.create(orderId, new SaveDeliveryRequest(version, input), user));
  }

  private DeliveriesView saveAs(
      UUID orderId, UUID deliveryId, long version, DeliveryContentInput input) {
    return inTenant(
        tenantId,
        () ->
            deliveries.update(orderId, deliveryId, new SaveDeliveryRequest(version, input), user));
  }

  private void shipTo(UUID partner, UUID card) {
    save(
        newOrder("PO-" + UUID.randomUUID()),
        content(partnerInput(partner), addressFromCard(card), null));
  }

  private List<UUID> cardsUsedFor(UUID partner) {
    return inTenant(tenantId, () -> deliveryRepository.shippedToCards(tenantId, partner));
  }

  private void verifyDelivery(UUID orderId, UUID deliveryId) {
    inTenant(
        tenantId,
        () ->
            deliveries.verify(
                orderId,
                deliveryId,
                new VerifyDeliveryRequest(versionInDatabase(orderId), null),
                verifier));
  }

  private int workRows(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_work_assignment"
            + " WHERE sales_order_id = ? AND work_kind = 'DELIVERY_VERIFICATION'",
        Integer.class,
        orderId);
  }

  private String workDepartment(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT department_code FROM sales_ord.order_work_assignment"
            + " WHERE sales_order_id = ? AND work_kind = 'DELIVERY_VERIFICATION'",
        String.class,
        orderId);
  }

  private boolean workClosed(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT closed_at IS NOT NULL FROM sales_ord.order_work_assignment"
            + " WHERE sales_order_id = ? AND work_kind = 'DELIVERY_VERIFICATION'",
        Boolean.class,
        orderId);
  }

  private List<String> workEvents(UUID orderId) {
    return jdbc.queryForList(
        "SELECT event_type FROM sales_ord.order_work_assignment_event"
            + " WHERE sales_order_id = ? AND work_kind = 'DELIVERY_VERIFICATION'"
            + " ORDER BY occurred_at, created_at",
        String.class,
        orderId);
  }

  private String deliveryStatus(UUID deliveryId) {
    return jdbc.queryForObject(
        "SELECT verification_status FROM sales_ord.order_delivery WHERE id = ?",
        String.class,
        deliveryId);
  }

  private String statusOf(String table, UUID id) {
    return jdbc.queryForObject(
        "SELECT verification_status FROM " + table + " WHERE id = ?", String.class, id);
  }

  private UUID verifiedByOf(String table, UUID id) {
    return jdbc.queryForObject(
        "SELECT verified_by FROM " + table + " WHERE id = ?", UUID.class, id);
  }

  private String columnDefault(String schema, String table) {
    return jdbc.queryForObject(
        "SELECT column_default FROM information_schema.columns WHERE table_schema = ?"
            + " AND table_name = ? AND column_name = 'verification_status'",
        String.class,
        schema,
        table);
  }

  private long versionInDatabase(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE tenant_id = ? AND id = ?",
        Long.class,
        tenantId,
        orderId);
  }

  private UUID newTenant() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return tenants.save(Tenant.create("Verify " + suffix, "DV-" + suffix)).getId();
  }

  private UUID newRegistry() {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Verify company " + UUID.randomUUID(), "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return registries.save(registry).getId();
  }

  private UUID newCustomer() {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Verify customer " + UUID.randomUUID(), "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return partners
        .saveAndFlush(
            TradingPartner.create(registries.save(registry), PartnerType.CUSTOMER, "Customer"))
        .getId();
  }

  private UUID newPartner(Verification state) {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Verify workshop " + UUID.randomUUID(), "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return partners
        .saveAndFlush(
            TradingPartner.create(
                registries.save(registry), PartnerType.DELIVERY_PARTNER, "Workshop", state))
        .getId();
  }

  private UUID newCard(Verification state) {
    return addresses
        .saveAndFlush(
            Address.builder()
                .streetAddress("1 Mill Lane")
                .city("Leeds")
                .country("United Kingdom")
                .countryCode("GB")
                .addressType(AddressType.SHIPPING)
                .verification(state)
                .build())
        .getId();
  }

  private <T> T inTenant(UUID tenant, Supplier<T> action) {
    try {
      TenantContext.setCurrentTenantId(tenant);
      TenantContext.setCurrentUserId(user);
      return action.get();
    } finally {
      TenantContext.clear();
    }
  }

  private Object attempt(Supplier<?> action) {
    try {
      return inTenant(tenantId, action);
    } catch (Throwable error) {
      return error;
    }
  }
}
