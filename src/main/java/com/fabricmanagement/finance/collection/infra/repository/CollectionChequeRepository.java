package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionChequeRepository extends JpaRepository<CollectionCheque, UUID> {
  Optional<CollectionCheque> findByTenantIdAndId(UUID tenant, UUID id);

  boolean existsByTenantIdAndClearedPaymentId(UUID tenant, UUID payment);

  /** PAYMENT-PLAN-9 R4: the cleared cheques whose clearing produced these payments. */
  List<CollectionCheque> findByTenantIdAndClearedPaymentIdInAndState(
      UUID tenant,
      java.util.Collection<UUID> payments,
      com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState state);

  /** PAYMENT-PLAN-9 R5: the customer's cheques in the given states. */
  List<CollectionCheque> findByTenantIdAndPartnerIdAndStateIn(
      UUID tenant,
      UUID partner,
      java.util.Collection<
              com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState>
          states);

  org.springframework.data.domain.Page<CollectionCheque> findByTenantIdAndPartnerIdAndStateNotIn(
      UUID tenant,
      UUID partner,
      java.util.Collection<
              com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState>
          states,
      org.springframework.data.domain.Pageable page);

  interface Reference {
    String getCurrency();

    String getInstrumentReference();
  }

  @Query(
      """
      select distinct c.currency as currency, c.instrumentReference as instrumentReference
      from CollectionCheque c
      where c.tenantId = :tenant and c.partnerId = :partner
        and c.currency in :currencies and c.instrumentReference in :references
      """)
  List<Reference> references(
      UUID tenant, UUID partner, Collection<String> currencies, Collection<String> references);

  boolean existsByTenantIdAndPartnerIdAndCurrencyAndInstrumentReferenceAndIdNot(
      UUID tenant, UUID partner, String currency, String reference, UUID id);
}
