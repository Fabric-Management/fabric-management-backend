package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoicePaymentAdapter implements InvoicePaymentPort {

  private final InvoiceRepository invoiceRepository;

  @Override
  public InvoiceAllocationView getInvoiceForAllocation(UUID tenantId, UUID invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoiceId)
            .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));

    return new InvoiceAllocationView(
        invoice.getId(),
        invoice.getAmountDue(),
        invoice.getCurrency(),
        invoice.getStatus().canReceivePayment());
  }

  @Override
  public void applyAllocation(UUID tenantId, UUID invoiceId, Money amount, LocalDate paymentDate) {
    Invoice invoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoiceId)
            .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));

    if (!invoice.getStatus().canReceivePayment()) {
      throw new FinanceDomainException("Invoice is not in a payable state");
    }

    invoice.applyAllocation(amount, paymentDate);
    invoiceRepository.save(invoice);
  }

  @Override
  public void reverseAllocation(UUID tenantId, UUID invoiceId, Money amount) {
    Invoice invoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoiceId)
            .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));

    invoice.reverseAllocation(amount);
    invoiceRepository.save(invoice);
  }
}
