package com.fabricmanagement.finance.invoice.app.scheduler;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceOverdueJob {

  private final InvoiceService invoiceService;
  private final TenantRepository tenantRepository;

  @Scheduled(cron = "0 0 2 * * *")
  public void markOverdueInvoicesForAllTenants() {
    log.info("Starting overdue invoice scan for all tenants");

    List<Tenant> activeTenants = tenantRepository.findAllActive();
    int totalMarked = 0;

    for (Tenant tenant : activeTenants) {
      try {
        int marked =
            TenantContext.executeInTenantContext(
                tenant.getId(),
                () -> {
                  TenantContext.setCurrentTenantUid(tenant.getUid());
                  return invoiceService.markOverdueInvoices(tenant.getId());
                });
        totalMarked += marked;
      } catch (Exception e) {
        log.error(
            "Failed to mark overdue invoices for tenant {}: {}", tenant.getUid(), e.getMessage());
      }
    }

    log.info(
        "Overdue invoice scan completed. Processed {} tenants, marked {} invoices as overdue.",
        activeTenants.size(),
        totalMarked);
  }
}
