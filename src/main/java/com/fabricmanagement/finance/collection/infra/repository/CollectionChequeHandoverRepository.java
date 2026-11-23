package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionChequeHandover;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionChequeHandoverRepository
    extends JpaRepository<CollectionChequeHandover, UUID> {
  String ACTIVE_APPLICATION =
      """
      a.tenantId = c.tenantId and a.chequeId = c.id and a.isActive = true and a.reversedAt is null
      """;

  // PP-9 R6: the creator governs an unapplied cheque; every active target governs an applied one.
  String VISIBLE_CHEQUE =
      """
      (:unrestricted = true or c.createdBy in :principals or exists (
        select 1 from CollectionChequeApplication a where
      """
          + ACTIVE_APPLICATION
          + """
      ))
      and not exists (
        select 1 from CollectionChequeApplication a where
      """
          + ACTIVE_APPLICATION
          + """
        and not exists (
          select 1 from CollectionObligation o
          where o.tenantId = c.tenantId and o.id = a.obligationId
            and (:unrestricted = true or o.createdBy in :principals or o.responsibleUserId in :principals)
        )
      )
      """;

  String VISIBLE_HANDOVERS =
      """
      from CollectionChequeHandover h
      where h.tenantId = :tenant
        and (:partner is null or h.partnerId = :partner)
        and (:number is null or upper(h.handoverNumber) = :number)
        and exists (
          select 1 from CollectionCheque c where c.tenantId = h.tenantId and c.handoverId = h.id and
      """
          + VISIBLE_CHEQUE
          + ")";

  Optional<CollectionChequeHandover> findByTenantIdAndId(UUID tenant, UUID id);

  List<CollectionChequeHandover> findByTenantIdAndIdIn(UUID tenant, Collection<UUID> ids);

  @Query(
      value = "select h " + VISIBLE_HANDOVERS,
      countQuery = "select count(h.id) " + VISIBLE_HANDOVERS)
  Page<CollectionChequeHandover> visibleHandovers(
      UUID tenant,
      UUID partner,
      String number,
      boolean unrestricted,
      Set<UUID> principals,
      Pageable page);

  @Query(
      """
      select c from CollectionCheque c
      where c.tenantId = :tenant and c.handoverId = :handover and
      """
          + VISIBLE_CHEQUE
          + " order by c.handoverOrdinal")
  List<CollectionCheque> visibleCheques(
      UUID tenant, UUID handover, boolean unrestricted, Set<UUID> principals);
}
