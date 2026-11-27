package com.fabricmanagement.production.quality.result.app;

import static com.fabricmanagement.notification.hub.domain.NotificationEventType.BATCH_CONCESSION_REVIEW;
import static com.fabricmanagement.notification.hub.domain.NotificationEventType.BATCH_MEASUREMENT_OUT_OF_LIMITS;
import static com.fabricmanagement.notification.hub.domain.NotificationEventType.BATCH_NO_QUALITY_STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationItemProcessor;
import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityStandardService;
import com.fabricmanagement.product.fiber.app.FiberReferenceQueryService;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest.CreateFiberQualityStandardRequestBuilder;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Outcome;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberMeasurementOutOfLimitsEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest.CreateFiberTestResultRequestBuilder;
import com.fabricmanagement.production.quality.result.dto.FiberTestEvaluationDto.FiberTestParameterEvaluationDto;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@RecordApplicationEvents
class FiberMeasurementNotificationIT extends AbstractFabricAppIntegrationTest {

  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantClonerService cloner;
  @Autowired private OrganizationRepository organisations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private PermissionTemplateRepository permissions;
  @Autowired private UserLocaleConfigRepository userLocales;
  @Autowired private FiberReferenceQueryService fibreReferences;
  @Autowired private FiberQualityStandardService profiles;
  @Autowired private BatchService batches;
  @Autowired private StockUnitService stockUnits;
  @Autowired private FiberTestResultService results;
  @Autowired private NotificationItemProcessor notificationProcessor;
  @Autowired private ApplicationEvents events;

  private UUID tenantId;
  private UUID organisationId;
  private UUID cottonIsoId;
  private UUID cottonProductId;
  private Role approverRole;
  private UUID approver;
  private String batchPrefix;
  private int batchNumber;

  @BeforeEach
  void prepareOrdinaryTenant() {
    tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    batchPrefix = "AB-" + suffix + "-";
    systemJdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status)"
            + " VALUES (?, ?, ?, 'Alder Beck Spinning Ltd', 'REGULAR', 'ACTIVE')",
        tenantId,
        "AB-" + suffix,
        "alder-beck-" + suffix);
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
    transactions.executeWithoutResult(
        status ->
            cloner.cloneNotificationDataInCallerTransaction(
                TenantContext.TEMPLATE_TENANT_ID, tenantId));
    organisationId =
        organisations
            .saveAndFlush(
                Organization.create(
                    "Alder Beck Spinning Ltd", "GB123456789", OrganizationType.SPINNER))
            .getId();

    approverRole = role("approve");
    approver = user("Eleanor", "Reed", approverRole, "EN");
    cottonIsoId = fibreReferences.findIsoCode("CO").orElseThrow().getId();
    cottonProductId =
        systemJdbc.queryForObject(
            """
            SELECT f.product_id FROM production.prod_fiber f
            JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id
            WHERE f.tenant_id = ? AND i.iso_code = 'CO' AND f.material_source IS NULL
            """,
            UUID.class,
            FiberCatalog.OWNER_ID);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void anOutOfLimitValueOnAReleasedLotNotifiesApproversAndChangesNothing() {
    UUID turkishApprover = user("Martha", "Shaw", approverRole, "TR");
    BatchDto batch = lotWithProfile(profile -> profile.moisturePctMax(10.0));
    UUID baleId =
        stockUnits
            .create(
                batch.getId(),
                ProductType.FIBER,
                batch.getBatchCode() + "-B1",
                null,
                PackageType.BALE,
                new BigDecimal("500"),
                null,
                "KG",
                null,
                null,
                null,
                StockUnitSourceType.PRODUCTION,
                batch.getId())
            .getId();
    batches.releaseFromQc(batch.getId());
    assertReleased(batch, baleId);

    FiberTestResultDto result = record(batch, reading -> reading.moisturePercent(11.0));

    assertThat(result.getApprovalStatus()).isEqualTo(TestApprovalStatus.REJECTED);
    var event = measurementEvent(result);
    assertThat(event.getAdditionalCount()).isZero();
    assertThat(deliver(batch, BATCH_MEASUREMENT_OUT_OF_LIMITS, event))
        .containsExactlyInAnyOrder(
            new Delivered(
                approver,
                "EN",
                "BATCH",
                "Batch " + batch.getBatchCode() + " has a measurement outside its limits",
                "The moisture measurement for batch "
                    + batch.getBatchCode()
                    + " is outside its limits."),
            new Delivered(
                turkishApprover,
                "TR",
                "BATCH",
                batch.getBatchCode() + " partisinde sınır dışında bir ölçüm var",
                batch.getBatchCode() + " partisinin nem ölçümü sınırlarının dışında."));
    assertReleased(batch, baleId);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void anIncompleteResultStillNotifiesItsOutOfLimitValue(boolean strengthWasMeasured) {
    BatchDto batch =
        lotWithProfile(
            profile -> {
              profile.strengthCndTexMin(25.0);
              if (strengthWasMeasured) {
                profile.uniformityIndexMin(80.0);
              } else {
                profile.moisturePctMax(10.0);
              }
            });
    FiberTestResultDto result =
        record(
            batch,
            reading -> {
              if (strengthWasMeasured) {
                reading.strengthCndTex(29.0).uniformityIndex(79.0);
              } else {
                reading.moisturePercent(11.0);
              }
            });

    assertThat(result.getApprovalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(result.getEvaluation().complete()).isFalse();
    assertThat(result.getEvaluation().outsideLimits()).isTrue();
    assertThat(outcomeOf(result, Parameter.STRENGTH))
        .isEqualTo(strengthWasMeasured ? Outcome.UNIT_UNCONFIRMED : Outcome.NOT_MEASURED);
    var event = measurementEvent(result);
    assertThat(event.getFirstParameter())
        .isEqualTo(strengthWasMeasured ? Parameter.UNIFORMITY_INDEX : Parameter.MOISTURE);
    String label = strengthWasMeasured ? "uniformity index" : "moisture";
    assertThat(deliver(batch, BATCH_MEASUREMENT_OUT_OF_LIMITS, event))
        .singleElement()
        .satisfies(
            notification -> {
              assertThat(notification.recipient()).isEqualTo(approver);
              assertThat(notification.body()).contains("The " + label + " measurement");
            });
  }

  @Test
  void twoOutOfLimitValuesSendOneNotification() {
    BatchDto batch =
        lotWithProfile(profile -> profile.moisturePctMax(10.0).trashContentPctMax(3.0));
    FiberTestResultDto result =
        record(batch, reading -> reading.trashContentPercent(4.0).moisturePercent(11.0));

    var event = measurementEvent(result);
    assertThat(event.getFirstParameter()).isEqualTo(Parameter.MOISTURE);
    assertThat(event.getAdditionalCount()).isEqualTo(1);
    assertThat(deliver(batch, BATCH_MEASUREMENT_OUT_OF_LIMITS, event))
        .singleElement()
        .satisfies(
            notification -> {
              assertThat(notification.recipient()).isEqualTo(approver);
              assertThat(notification.body())
                  .contains(
                      "The moisture measurement",
                      "The number of other measurements outside their limits is 1.")
                  .doesNotContain("trash content", "TRASH_CONTENT", "[[", "{additionalCount}");
            });
  }

  @ParameterizedTest
  @ValueSource(doubles = {28.0, 50.0})
  void aValueWhoseUnitIsUnconfirmedNotifiesNobody(double strength) {
    BatchDto batch =
        lotWithProfile(profile -> profile.strengthCndTexMin(25.0).strengthCndTexMax(30.0));
    FiberTestResultDto result = record(batch, reading -> reading.strengthCndTex(strength));

    assertThat(result.getApprovalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    assertThat(outcomeOf(result, Parameter.STRENGTH)).isEqualTo(Outcome.UNIT_UNCONFIRMED);
    assertNoQualityNotification(batch);
  }

  @Test
  void onlyApproversHearOfAnOutOfLimitValue() {
    UUID writer = user("Ellis", "Ford", role("write"), "EN");
    UUID manager = user("Lucy", "Bell", role("manage"), "EN");
    BatchDto profiled = lotWithProfile(profile -> profile.moisturePctMax(10.0));
    FiberTestResultDto result = record(profiled, reading -> reading.moisturePercent(11.0));

    assertThat(deliver(profiled, BATCH_MEASUREMENT_OUT_OF_LIMITS, measurementEvent(result)))
        .extracting(Delivered::recipient)
        .containsExactly(approver)
        .doesNotContain(writer, manager);

    BatchDto unprofiled = lot(null);
    var pending = record(unprofiled, reading -> reading.moisturePercent(7.0));
    assertThat(pending.getApprovalStatus()).isEqualTo(TestApprovalStatus.PENDING);
    var event =
        events.stream(BatchQualityStandardMissingEvent.class)
            .filter(candidate -> unprofiled.getId().equals(candidate.getBatchId()))
            .findFirst()
            .orElseThrow();
    assertThat(deliver(unprofiled, BATCH_NO_QUALITY_STANDARD, event))
        .extracting(Delivered::recipient)
        .containsExactly(manager)
        .doesNotContain(writer, approver);
    assertNoMeasurementOrConcessionNotification(unprofiled);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void anOffTargetValueNotifiesNobody(boolean hasLimit) {
    BatchDto batch =
        lotWithProfile(
            profile -> {
              profile.moisturePctTarget(5.0);
              if (hasLimit) {
                profile.moisturePctMax(10.0);
              }
            });
    var result = record(batch, reading -> reading.moisturePercent(hasLimit ? 7.0 : 11.0));

    assertThat(result.getApprovalStatus())
        .isEqualTo(hasLimit ? TestApprovalStatus.APPROVED : TestApprovalStatus.PENDING);
    assertThat(result.getEvaluation().complete()).isTrue();
    assertThat(result.getEvaluation().limitDefined()).isEqualTo(hasLimit);
    assertThat(result.getEvaluation().parameters())
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value.outcome())
                  .isEqualTo(hasLimit ? Outcome.WITHIN_LIMITS : Outcome.NO_LIMIT);
              assertThat(value.differenceFromTarget()).isEqualTo(hasLimit ? 2.0 : 6.0);
            });
    assertNoQualityNotification(batch);
  }

  private FiberMeasurementOutOfLimitsEvent measurementEvent(FiberTestResultDto result) {
    List<FiberMeasurementOutOfLimitsEvent> published =
        events.stream(FiberMeasurementOutOfLimitsEvent.class)
            .filter(event -> result.getId().equals(event.getResultId()))
            .toList();
    assertThat(published).hasSize(1);
    return published.getFirst();
  }

  private List<Delivered> deliver(BatchDto batch, String type, DomainEvent event) {
    // Wait for the asynchronous after-commit transaction before counting recipients.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        systemJdbc.queryForObject(
                            "SELECT count(*) FROM event_publication"
                                + " WHERE serialized_event LIKE ? AND completion_date IS NOT NULL"
                                + " AND listener_id LIKE '%QualityNotificationListener.%'",
                            Long.class, "%" + event.getEventId() + "%"))
                    .isEqualTo(1L));
    List<UUID> queued = queuedFor(batch, type);
    assertThat(queued).isNotEmpty();
    for (UUID queueId : queued) {
      notificationProcessor.processItem(queueId);
    }
    return deliveredFor(batch, type);
  }

  private void assertNoMeasurementOrConcessionNotification(BatchDto batch) {
    assertThat(
            events.stream(FiberMeasurementOutOfLimitsEvent.class)
                .filter(event -> batch.getId().equals(event.getBatchId())))
        .isEmpty();
    assertThat(
            events.stream(FiberTestResultApprovedEvent.class)
                .filter(event -> batch.getId().equals(event.getBatchId())))
        .isEmpty();
    for (String type : List.of(BATCH_MEASUREMENT_OUT_OF_LIMITS, BATCH_CONCESSION_REVIEW)) {
      assertThat(queuedFor(batch, type)).isEmpty();
      assertThat(deliveredFor(batch, type)).isEmpty();
    }
  }

  private void assertNoQualityNotification(BatchDto batch) {
    assertNoMeasurementOrConcessionNotification(batch);
    assertThat(
            events.stream(BatchQualityStandardMissingEvent.class)
                .filter(event -> batch.getId().equals(event.getBatchId())))
        .isEmpty();
    assertThat(queuedFor(batch, BATCH_NO_QUALITY_STANDARD)).isEmpty();
    assertThat(deliveredFor(batch, BATCH_NO_QUALITY_STANDARD)).isEmpty();
  }

  private List<UUID> queuedFor(BatchDto batch, String type) {
    return systemJdbc.queryForList(
        """
        SELECT id FROM notification.notification_queue
        WHERE tenant_id = ? AND event_type = ? AND payload ->> 'referenceId' = ?
        """,
        UUID.class,
        tenantId,
        type,
        batch.getId().toString());
  }

  private List<Delivered> deliveredFor(BatchDto batch, String type) {
    return systemJdbc.query(
        """
        SELECT recipient_id, locale, reference_type, title, body FROM notification.notification_log
        WHERE tenant_id = ? AND event_type = ? AND reference_id = ? AND channel = 'IN_APP'
        """,
        (row, index) ->
            new Delivered(
                row.getObject("recipient_id", UUID.class),
                row.getString("locale"),
                row.getString("reference_type"),
                row.getString("title"),
                row.getString("body")),
        tenantId,
        type,
        batch.getId());
  }

  private void assertReleased(BatchDto batch, UUID baleId) {
    assertThat(batches.getById(batch.getId()).orElseThrow().getStatus())
        .isEqualTo(BatchStatus.AVAILABLE);
    assertThat(
            systemJdbc.queryForObject(
                "SELECT quality_disposition FROM production.stock_unit WHERE tenant_id = ? AND id = ?",
                String.class,
                tenantId,
                baleId))
        .isEqualTo("RELEASED");
    assertThat(
            systemJdbc.queryForObject(
                "SELECT count(*) FROM production.quality_decision WHERE tenant_id = ? AND batch_id = ?",
                Long.class,
                tenantId,
                batch.getId()))
        .isEqualTo(1L);
  }

  private BatchDto lotWithProfile(Consumer<CreateFiberQualityStandardRequestBuilder> criteria) {
    var profile =
        CreateFiberQualityStandardRequest.builder()
            .targetType(FiberQualityTargetType.ISO_CODE)
            .isoCodeId(cottonIsoId)
            .standardName("Alder Beck cotton " + (batchNumber + 1));
    criteria.accept(profile);
    return lot(profiles.create(profile.build()).getId());
  }

  private BatchDto lot(UUID profileId) {
    return batches.create(
        CreateBatchRequest.builder()
            .productId(cottonProductId)
            .productType(ProductType.FIBER)
            .batchCode(batchPrefix + ++batchNumber)
            .quantity(new BigDecimal("500"))
            .unit("KG")
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .qualityStandardId(profileId)
            .build());
  }

  private FiberTestResultDto record(
      BatchDto batch, Consumer<CreateFiberTestResultRequestBuilder> measurements) {
    var request =
        CreateFiberTestResultRequest.builder()
            .batchId(batch.getId())
            .testDate(Instant.now().minusSeconds(60));
    measurements.accept(request);
    return results.create(request.build());
  }

  private Role role(String action) {
    String code = "FIBRE_QC_" + action.toUpperCase(Locale.ROOT);
    Role role = roles.saveAndFlush(Role.create(code, code, "Fibre quality " + action));
    permissions.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(code)
            .resource("quality")
            .action(action)
            .dataScope(DataScope.ORGANIZATION)
            .build());
    return role;
  }

  private UUID user(String firstName, String lastName, Role role, String locale) {
    User user = User.create(firstName, lastName, organisationId);
    user.setRole(role);
    UUID userId = users.saveAndFlush(user).getId();
    userLocales.saveAndFlush(UserLocaleConfig.create(tenantId, userId, locale));
    return userId;
  }

  private static Outcome outcomeOf(FiberTestResultDto result, Parameter parameter) {
    return result.getEvaluation().parameters().stream()
        .filter(value -> value.parameter() == parameter)
        .findFirst()
        .map(FiberTestParameterEvaluationDto::outcome)
        .orElseThrow();
  }

  private record Delivered(
      UUID recipient, String locale, String reference, String title, String body) {}
}
