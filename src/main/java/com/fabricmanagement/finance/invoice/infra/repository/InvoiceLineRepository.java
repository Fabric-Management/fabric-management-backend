package com.fabricmanagement.finance.invoice.infra.repository;

import com.fabricmanagement.finance.invoice.domain.InvoiceLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

  List<InvoiceLine> findByInvoiceIdOrderByLineNumberAsc(UUID invoiceId);

  void deleteByInvoiceIdAndId(UUID invoiceId, UUID lineId);

  int countByInvoiceId(UUID invoiceId);
}
