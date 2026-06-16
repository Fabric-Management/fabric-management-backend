package com.fabricmanagement.finance.payment.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.fx.infra.repository.FxRealizationRepository;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PaymentPersistenceIT {

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("fabric")
          .withUsername("fabric")
          .withPassword("fabric");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private PaymentService paymentService;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private FxRealizationRepository fxRealizationRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID tradingPartnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);

    jdbcTemplate.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status, is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, 'ACTIVE', true, now(), now(), 0)",
        tenantId,
        UUID.randomUUID().toString(),
        "test-tenant-" + UUID.randomUUID().toString().substring(0, 8),
        "Test Tenant");

    UUID registryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO common_company.trading_partner_registry (id, uid, tax_id, official_name, verified_status, is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, 'UNVERIFIED', true, now(), now(), 0)",
        registryId,
        UUID.randomUUID().toString(),
        "1234567890",
        "Test Official Name");

    jdbcTemplate.update(
        "INSERT INTO common_company.common_trading_partner (id, tenant_id, uid, registry_id, custom_name, partner_type, status, is_active, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, 'CUSTOMER', 'ACTIVE', true, now(), now(), 0)",
        tradingPartnerId,
        tenantId,
        UUID.randomUUID().toString(),
        registryId,
        "Test Partner");
  }

  @AfterEach
  void tearDown() {
    fxRealizationRepository.deleteAll();
    paymentRepository.deleteAll();
    invoiceRepository.deleteAll();
    jdbcTemplate.update(
        "DELETE FROM common_company.common_trading_partner WHERE id = ?", tradingPartnerId);
    jdbcTemplate.update("DELETE FROM common_company.trading_partner_registry");
    jdbcTemplate.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
    TenantContext.clear();
  }

  @Test
  void testOptimisticLockingOnInvoiceAllocation() {
    // 1. Create an Invoice
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(tradingPartnerId)
            .invoiceNumber("INV-OPT-1")
            .issueDate(LocalDate.now())
            .dueDate(LocalDate.now().plusDays(30))
            .subtotal(Money.of(new java.math.BigDecimal("100.00"), "GBP"))
            .totalAmount(Money.of(new java.math.BigDecimal("100.00"), "GBP"))
            .amountDue(Money.of(new java.math.BigDecimal("100.00"), "GBP"))
            .amountPaid(Money.of(java.math.BigDecimal.ZERO, "GBP"))
            .amountCredited(Money.of(java.math.BigDecimal.ZERO, "GBP"))
            .status(InvoiceStatus.SENT)
            .build();
    invoice.setTenantId(tenantId);
    invoice = invoiceRepository.saveAndFlush(invoice);

    // 2. Create a Payment
    PaymentDto paymentDto =
        paymentService.createPayment(
            new CreatePaymentRequest(
                invoice.getTradingPartnerId(),
                PaymentDirection.INBOUND.name(),
                "BANK_TRANSFER",
                new BigDecimal("100.00"),
                "GBP",
                LocalDate.now(),
                "REF-1",
                "Notes",
                Collections.emptyList()));

    PaymentDto payment2Dto =
        paymentService.createPayment(
            new CreatePaymentRequest(
                invoice.getTradingPartnerId(),
                PaymentDirection.INBOUND.name(),
                "BANK_TRANSFER",
                new BigDecimal("100.00"),
                "GBP",
                LocalDate.now(),
                "REF-2",
                "Notes",
                Collections.emptyList()));

    // 3. Simulate Concurrent Threads
    // Both threads read the same invoice version
    UUID invoiceId = invoice.getId();

    // Thread 1: Allocate 50
    CreateAllocationRequest req1 = new CreateAllocationRequest(invoiceId, new BigDecimal("50.00"));
    paymentService.allocatePayment(paymentDto.id(), req1);

    // Because PaymentService runs in @Transactional and we are not in an explicit transaction in
    // the test,
    // the first allocatePayment commits and increments the invoice version in the DB.

    // Thread 2: Allocate 50. But wait, allocatePayment does `getInvoiceForAllocation` which fetches
    // the latest invoice version from DB, so it wouldn't throw OptimisticLockException here
    // unless the fetch and save are interleaved between threads.
    // To simulate true interleaving, we must use a CountDownLatch or manually load and save.
    // Since we want to test that BaseEntity.@Version works, we can manually do it here:

    Invoice tx1Invoice = invoiceRepository.findById(invoiceId).orElseThrow();
    Invoice tx2Invoice = invoiceRepository.findById(invoiceId).orElseThrow();

    tx1Invoice.applyAllocation(Money.of(new java.math.BigDecimal("10.00"), "GBP"), LocalDate.now());
    invoiceRepository.saveAndFlush(tx1Invoice); // version increments

    tx2Invoice.applyAllocation(Money.of(new java.math.BigDecimal("10.00"), "GBP"), LocalDate.now());

    assertThatThrownBy(() -> invoiceRepository.saveAndFlush(tx2Invoice))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }
}
