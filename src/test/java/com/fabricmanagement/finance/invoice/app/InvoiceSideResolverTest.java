package com.fabricmanagement.finance.invoice.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceSideResolverTest {

  @Mock private InvoiceRepository invoiceRepository;

  @Test
  void resolveSide_creditNoteFollowsOriginalInvoiceSide() {
    UUID tenantId = UUID.randomUUID();
    UUID originalId = UUID.randomUUID();

    Invoice original = Invoice.builder().invoiceType(InvoiceType.PURCHASE).build();
    original.setId(originalId);

    Invoice creditNote =
        Invoice.builder()
            .invoiceType(InvoiceType.CREDIT_NOTE)
            .originalInvoiceId(originalId)
            .build();
    creditNote.setId(UUID.randomUUID());

    when(invoiceRepository.findByTenantIdAndId(tenantId, originalId))
        .thenReturn(Optional.of(original));

    InvoiceSide side = new InvoiceSideResolver(invoiceRepository).resolveSide(tenantId, creditNote);

    assertThat(side).isEqualTo(InvoiceSide.ACCOUNTS_PAYABLE);
  }
}
