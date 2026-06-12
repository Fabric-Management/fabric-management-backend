package com.fabricmanagement.finance.invoice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceLineRequest;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("Invoice Persistence Integration Test")
class InvoicePersistenceIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
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

  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private NamedParameterJdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);

    // Insert a tenant to satisfy the foreign key
    jdbc.getJdbcOperations()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status, is_active, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', true, now(), now(), 0)",
            tenantId,
            UUID.randomUUID().toString(),
            "test-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Test Tenant");

    UUID registryId = UUID.randomUUID();
    jdbc.getJdbcOperations()
        .update(
            "INSERT INTO common_company.trading_partner_registry (id, uid, tax_id, official_name, verified_status, is_active, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, 'UNVERIFIED', true, now(), now(), 0)",
            registryId,
            UUID.randomUUID().toString(),
            "1234567890",
            "Test Official Name");

    // Insert a trading partner to satisfy the foreign key
    jdbc.getJdbcOperations()
        .update(
            "INSERT INTO common_company.common_trading_partner (id, tenant_id, uid, registry_id, custom_name, partner_type, status, is_active, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, ?, 'CUSTOMER', 'ACTIVE', true, now(), now(), 0)",
            partnerId,
            tenantId,
            UUID.randomUUID().toString(),
            registryId,
            "Test Partner");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    invoiceRepository.deleteAll();
    jdbc.getJdbcOperations()
        .update("DELETE FROM common_company.common_trading_partner WHERE id = ?", partnerId);
    jdbc.getJdbcOperations().update("DELETE FROM common_company.trading_partner_registry");
    jdbc.getJdbcOperations()
        .update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
  }

  @Test
  @DisplayName("Create invoice with lines should persist lines and set invoiceId correctly")
  void createInvoice_withLines_shouldPersistLinesAndSetInvoiceId() {
    // 1. Create through service
    CreateInvoiceRequest request =
        new CreateInvoiceRequest(
            partnerId, // tradingPartnerId
            null, // orderReference
            null, // externalReference
            "SALES", // invoiceType
            LocalDate.now(), // issueDate
            LocalDate.now().plusDays(30), // dueDate
            new BigDecimal("2000.00"), // subtotal
            null, // taxAmount
            null, // discountAmount
            new BigDecimal("2242.00"), // totalAmount
            "GBP", // currency
            new BigDecimal("18"), // taxRate
            null, // billingAddress
            null, // notes
            null, // originalInvoiceId
            List.of(
                new CreateInvoiceLineRequest(
                    "Item 1",
                    "PROD1",
                    "KG",
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    BigDecimal.ZERO,
                    new BigDecimal("18"),
                    null),
                new CreateInvoiceLineRequest(
                    "Item 2",
                    "PROD2",
                    "PCS",
                    new BigDecimal("50"),
                    new BigDecimal("20"),
                    new BigDecimal("10"),
                    new BigDecimal("18"),
                    null)));

    InvoiceDto createdDto = invoiceService.createInvoice(request);

    // 2. No need to flush/clear because service already committed and closed its transaction.
    // 3. Reload from DB inside a transaction to initialize lazy collections
    transactionTemplate.executeWithoutResult(
        status -> {
          Invoice loaded = invoiceRepository.findById(createdDto.id()).orElseThrow();

          // 4. Assertions
          assertThat(loaded.getLines()).hasSize(2);

          loaded
              .getLines()
              .forEach(
                  line -> {
                    assertThat(line.getId()).isNotNull();
                    assertThat(line.getInvoiceId())
                        .as("Line invoiceId must be populated")
                        .isEqualTo(loaded.getId());
                  });

          // Check header amounts are derived correctly
          // Line 1: 100 * 10 = 1000 + 18% tax = 1180
          // Line 2: 50 * 20 = 1000 - 10% discount = 900 + 18% tax = 1062
          // Expected Subtotal: 2000
          // Expected Discount: 100
          // Expected Tax: 180 + 162 = 342
          // Expected Total: 2000 - 100 + 342 = 2242
          assertThat(loaded.getSubtotal().getAmount())
              .isEqualByComparingTo(new BigDecimal("2000.00"));
          assertThat(loaded.getDiscountAmount().getAmount())
              .isEqualByComparingTo(new BigDecimal("100.00"));
          assertThat(loaded.getTaxAmount().getAmount())
              .isEqualByComparingTo(new BigDecimal("342.00"));
          assertThat(loaded.getTotalAmount().getAmount())
              .isEqualByComparingTo(new BigDecimal("2242.00"));
        });
  }

  @Test
  @DisplayName("Create invoice with mismatched total should throw domain exception")
  void createInvoice_withMismatchedTotal_throwsException() {
    CreateInvoiceRequest request =
        new CreateInvoiceRequest(
            partnerId,
            null,
            null,
            "SALES",
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            new BigDecimal("1000.00"), // Mismatched subtotal
            null,
            null,
            new BigDecimal("9999.00"), // Mismatched total
            "GBP",
            new BigDecimal("18"),
            null,
            null,
            null,
            List.of(
                new CreateInvoiceLineRequest(
                    "Item 1",
                    "PROD1",
                    "KG",
                    new BigDecimal("100"),
                    new BigDecimal("10"), // subtotal 1000
                    BigDecimal.ZERO,
                    new BigDecimal("18"), // tax 180, total 1180
                    null)));

    // Should throw FinanceDomainException with message mentioning line-derived subtotal or total
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> invoiceService.createInvoice(request))
        .isInstanceOf(com.fabricmanagement.finance.common.exception.FinanceDomainException.class)
        .hasMessageContaining("does not match line-derived");
  }
}
