package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CollectionObligationRepository
    extends JpaRepository<CollectionObligation, UUID>,
        JpaSpecificationExecutor<CollectionObligation> {
  Optional<CollectionObligation> findByTenantIdAndId(UUID tenant, UUID id);

  List<CollectionObligation> findByTenantIdAndOrderId(UUID tenant, UUID order);

  List<CollectionObligation> findByTenantIdAndIdIn(UUID tenant, List<UUID> ids);

  /**
   * The expectations that still wait for money and whose date has come: not cancelled, with more
   * agreed than recorded cash (as {@code CollectionPaymentMatchRepository#settled} counts it), and
   * a date, agreed or cheque, that is {@code today} or earlier. In id order, after {@code after}.
   */
  @org.springframework.data.jpa.repository.Query(
      value =
          """
          SELECT o.id FROM finance.collection_obligation o
          WHERE o.tenant_id=:tenant AND o.is_active AND NOT o.cancelled AND o.id>:after
            AND (o.expected_date<=:today OR o.cheque_payment_date<=:today)
            AND o.amount > COALESCE((
              SELECT sum(m.amount) FROM finance.collection_payment_match m
              JOIN finance.finance_payment p ON p.tenant_id=m.tenant_id AND p.id=m.payment_id
              WHERE m.tenant_id=o.tenant_id AND m.obligation_id=o.id AND m.reversed_at IS NULL
                AND p.status<>'VOIDED' AND p.is_active AND m.is_active),0)
          ORDER BY o.id LIMIT :limit
          """,
      nativeQuery = true)
  List<UUID> findOpenDueIds(UUID tenant, java.time.LocalDate today, UUID after, int limit);
}
