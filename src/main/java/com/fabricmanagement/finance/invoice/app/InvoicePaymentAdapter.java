package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.fx.app.RealizedFxService;
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
  private final RealizedFxService realizedFxService;

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
        invoice.getStatus().canReceivePayment() && invoice.getInvoiceType().isSettleable());
  }

  @Override
  public void applyAllocation(UUID tenantId, UUID invoiceId, Money amount, LocalDate paymentDate) {
    Invoice invoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoiceId)
            .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));

    if (!invoice.getStatus().canReceivePayment()) {
      throw new FinanceDomainException("Invoice is not in a payable status");
    }
    if (!invoice.getInvoiceType().isSettleable()) {
      throw new FinanceDomainException("Invoice type cannot be settled directly");
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

  @Override
  public SettlementFxResult recordAllocationFx(
      UUID tenantId, UUID invoiceId, UUID allocationId, Money amount, LocalDate settlementDate) {
    Invoice invoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, invoiceId)
            .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));

    return realizedFxService.recordPaymentAllocation(
        tenantId, invoice, allocationId, amount, settlementDate);
  }

  @Override
  public void reverseAllocationFx(UUID tenantId, UUID allocationId) {
    realizedFxService.reversePaymentAllocation(tenantId, allocationId);
  }
}
