package com.fabricmanagement.procurement.quote.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DataScopeGuard;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.procurement.purchaseorder.app.validation.PurchaseOrderValidationEngine;
import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteModuleType;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuoteToOrderOrchestratorAsyncTenantIT {

  private static final UUID TENANT_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            "DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN "
                + "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'; "
                + "END IF; END $$");
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to create fabric_app role", e);
    }

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "app_test");
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private SupplierRFQRepository rfqRepository;
  @Autowired private SupplierQuoteRepository quoteRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private PurchaseOrderValidationEngine validationEngine;
  @MockBean private ApprovalPort approvalPort;
  @MockBean private DataScopeGuard dataScopeGuard;
  @MockBean private TenantReportingCurrencyPort tenantReportingCurrencyPort;
  @MockBean private ExchangeRateService exchangeRateService;

  private UUID quoteId;
  private UUID rfqId;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    when(dataScopeGuard.canAccess(eq("procurement"), eq("write"), any(BaseEntity.class)))
        .thenReturn(true);
    when(tenantReportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn("USD");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void supplierQuoteAcceptedPublishedWithoutTenantContextCreatesPurchaseOrder() throws Exception {
    assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class))
        .isEqualTo("fabric_app");
    Boolean bypassesRls =
        jdbcTemplate.queryForObject(
            "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
    assertThat(bypassesRls).isFalse();

    seedTenantAsOwner();
    seedAcceptedQuote();
    SupplierQuoteAcceptedEvent event = new SupplierQuoteAcceptedEvent(TENANT_ID, quoteId, rfqId);
    TenantContext.clear();

    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(countPurchaseOrdersForQuoteAsOwner())
                    .as("purchase_order row for accepted supplier quote")
                    .isEqualTo(1));
  }

  private void seedAcceptedQuote() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentTenantUid("ASYNC-TENANT");

    transactionTemplate.executeWithoutResult(
        status -> {
          SupplierRFQLine rfqLine = new SupplierRFQLine();
          rfqLine.setProductDesc("Async tenant probe material");
          rfqLine.setRequestedQty(BigDecimal.TEN);
          rfqLine.setUnit("KG");

          SupplierRFQ rfq = new SupplierRFQ();
          rfq.setRfqNumber("ASYNC-RFQ-" + UUID.randomUUID());
          rfq.setWorkOrderId(UUID.randomUUID());
          rfq.setModuleType(SupplierRFQModuleType.GENERIC);
          rfq.setRfqType(SupplierRFQType.PURCHASE);
          rfq.setStatus(SupplierRFQStatus.SENT);
          rfq.setDeadline(Instant.now().plus(Duration.ofDays(7)));
          rfq.addLine(rfqLine);
          SupplierRFQ savedRfq = rfqRepository.saveAndFlush(rfq);

          SupplierQuoteLine quoteLine = new SupplierQuoteLine();
          quoteLine.setRfqLineId(savedRfq.getLines().get(0).getId());
          quoteLine.setUnitPrice(BigDecimal.valueOf(12));
          quoteLine.setCurrency("USD");
          quoteLine.setQty(BigDecimal.TEN);
          quoteLine.setUnit("KG");

          SupplierQuote quote = new SupplierQuote();
          quote.setQuoteNumber("ASYNC-SQ-" + UUID.randomUUID());
          quote.setRfqId(savedRfq.getId());
          quote.setTradingPartnerId(UUID.randomUUID());
          quote.setStatus(SupplierQuoteStatus.RECEIVED);
          quote.setModuleType(SupplierQuoteModuleType.GENERIC);
          quote.setValidUntil(LocalDate.now().plusDays(30));
          quote.setCurrency("USD");
          quote.setEntryMethod(QuoteEntryMethod.MANUAL_ENTRY);
          quote.setReportingTotal(
              ConvertedMoney.of(
                  BigDecimal.valueOf(120),
                  "USD",
                  BigDecimal.valueOf(120),
                  "USD",
                  BigDecimal.ONE,
                  LocalDate.now()));
          quote.addLine(quoteLine);
          quote.accept();

          SupplierQuote savedQuote = quoteRepository.saveAndFlush(quote);
          rfqId = savedRfq.getId();
          quoteId = savedQuote.getId();
        });

    TenantContext.clear();
  }

  private void seedTenantAsOwner() throws Exception {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            "INSERT INTO common_tenant.common_tenant "
                + "(id, uid, slug, name, status, settings, is_active, created_at, updated_at, version) "
                + "VALUES ('"
                + TENANT_ID
                + "', 'ASYNC-TENANT', 'async-tenant', 'Async Tenant Test', 'ACTIVE', "
                + "'{}'::jsonb, true, now(), now(), 0) "
                + "ON CONFLICT (id) DO NOTHING");
      }
    }
  }

  private int countPurchaseOrdersForQuoteAsOwner() throws Exception {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (var ps =
          conn.prepareStatement(
              "SELECT count(*) FROM procurement.purchase_order WHERE tenant_id = ? AND supplier_quote_id = ?")) {
        ps.setObject(1, TENANT_ID);
        ps.setObject(2, quoteId);
        try (var rs = ps.executeQuery()) {
          rs.next();
          return rs.getInt(1);
        }
      }
    }
  }
}
