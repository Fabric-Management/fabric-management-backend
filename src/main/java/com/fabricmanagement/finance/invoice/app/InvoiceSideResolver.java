package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceSideResolver {

  private final InvoiceRepository invoiceRepository;

  public InvoiceSide resolveSide(UUID tenantId, Invoice invoice) {
    return resolveSide(tenantId, invoice, new HashSet<>());
  }

  private InvoiceSide resolveSide(UUID tenantId, Invoice invoice, Set<UUID> visitedInvoiceIds) {
    if (invoice.getInvoiceType().isReceivable()) {
      return InvoiceSide.ACCOUNTS_RECEIVABLE;
    }
    if (invoice.getInvoiceType().isPayable()) {
      return InvoiceSide.ACCOUNTS_PAYABLE;
    }
    if (!invoice.getInvoiceType().isSideDerived()) {
      throw new FinanceDomainException(
          "Invoice type has no AR/AP side: " + invoice.getInvoiceType());
    }
    if (invoice.getOriginalInvoiceId() == null) {
      throw new FinanceDomainException("CREDIT_NOTE originalInvoiceId is required to resolve side");
    }
    if (!visitedInvoiceIds.add(invoice.getId())) {
      throw new FinanceDomainException("Invoice side resolution cycle detected");
    }

    Invoice original =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoice.getOriginalInvoiceId())
            .orElseThrow(
                () ->
                    new FinanceDomainException(
                        "Original invoice not found for side resolution: "
                            + invoice.getOriginalInvoiceId()));
    return resolveSide(tenantId, original, visitedInvoiceIds);
  }
}
