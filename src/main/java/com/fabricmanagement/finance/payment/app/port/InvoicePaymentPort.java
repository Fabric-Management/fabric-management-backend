package com.fabricmanagement.finance.payment.app.port;

import com.fabricmanagement.common.util.Money;
import java.time.LocalDate;
import java.util.UUID;

public interface InvoicePaymentPort {

  InvoiceAllocationView getInvoiceForAllocation(UUID tenantId, UUID invoiceId);

  void applyAllocation(UUID tenantId, UUID invoiceId, Money amount, LocalDate paymentDate);

  void reverseAllocation(UUID tenantId, UUID invoiceId, Money amount);
}
