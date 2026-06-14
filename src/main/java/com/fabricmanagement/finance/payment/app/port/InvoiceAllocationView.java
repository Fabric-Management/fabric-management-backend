package com.fabricmanagement.finance.payment.app.port;

import com.fabricmanagement.common.util.Money;
import java.util.UUID;

public record InvoiceAllocationView(
    UUID invoiceId, Money openBalance, String currency, boolean payable) {}
