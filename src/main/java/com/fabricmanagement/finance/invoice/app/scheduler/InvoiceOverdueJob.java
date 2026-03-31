package com.fabricmanagement.finance.invoice.app.scheduler;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
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
  private final TenantQueryPort tenantQueryPort;

  @Scheduled(cron = "0 0 2 * * *")
  public void markOverdueInvoicesForAllTenants() {
    log.info("Starting overdue invoice scan for all tenants");

    List<TenantReference> activeTenants = tenantQueryPort.findAllActiveTenants();
    int totalMarked = 0;

    for (TenantReference tenant : activeTenants) {
      try {
        int marked =
            TenantContext.executeInTenantContext(
                tenant.id(),
                () -> {
                  TenantContext.setCurrentTenantUid(tenant.uid());
                  return invoiceService.markOverdueInvoices(tenant.id());
                });
        totalMarked += marked;
      } catch (Exception e) {
        log.error(
            "Failed to mark overdue invoices for tenant {}: {}", tenant.uid(), e.getMessage());
      }
    }

    log.info(
        "Overdue invoice scan completed. Processed {} tenants, marked {} invoices as overdue.",
        activeTenants.size(),
        totalMarked);
  }
}
