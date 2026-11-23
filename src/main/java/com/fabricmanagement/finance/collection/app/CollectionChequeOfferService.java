package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.CollectionAmounts;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionCustomerSummaryDtos.Offer;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeOfferRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeOfferRepository.Evidence;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionChequeOfferService {
  private final CollectionAccessService access;
  private final CollectionCommandStore commands;
  private final CollectionChequeRepository cheques;
  private final CollectionObligationRepository obligations;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionProjectionRepository projections;
  private final CollectionReconciliationHealthService health;
  private final CollectionChequeOfferRepository offers;
  private final CollectionEvents events;

  @Transactional
  public Offer record(UUID chequeId, UUID instalmentId, UUID actor) {
    var write = access.reach(actor, "write");
    var read = access.reach(actor, "read");
    if (write.scope() == null || read.scope() == null) {
      throw new AccessDeniedException("Finance read and write access are required");
    }
    var tenant = write.tenantId();
    commands.lock();
    var cheque =
        cheques
            .findByTenantIdAndId(tenant, chequeId)
            .filter(
                c ->
                    read.permits(tenant, c.getCreatedBy(), null)
                        && write.permits(tenant, c.getCreatedBy(), null))
            .orElseThrow(() -> new NotFoundException("Cheque not found"));
    var instalment =
        obligations
            .findByTenantIdAndId(tenant, instalmentId)
            .filter(
                o ->
                    read.permits(tenant, o.getCreatedBy(), o.getResponsibleUserId())
                        && write.permits(tenant, o.getCreatedBy(), o.getResponsibleUserId()))
            .orElseThrow(() -> new NotFoundException("Collection instalment not found"));
    var existing = offers.find(tenant, chequeId);
    if (existing.isPresent()) {
      if (!existing.get().obligationId().equals(instalmentId)) {
        throw incompatible("A confirmed offer already exists for this cheque");
      }
      return dto(existing.get());
    }
    if (!Boolean.TRUE.equals(cheque.getIsActive())
        || (cheque.getState() != ChequeState.RECEIVED && cheque.getState() != ChequeState.PRESENTED)
        || !applications.active(tenant, chequeId).isEmpty()
        || !Boolean.TRUE.equals(instalment.getIsActive())
        || instalment.isCancelled()
        || instalment.getMethod() != Method.BANK_TRANSFER
        || !instalment.getPartnerId().equals(cheque.getPartnerId())
        || !instalment.getCurrency().equals(cheque.getCurrency())) {
      throw incompatible(
          "Choose an unallocated held cheque and a transfer instalment for the same customer and currency");
    }
    var projection = projections.findByTenantIdAndOrderId(tenant, instalment.getOrderId());
    var settled =
        matches.settled(tenant, List.of(instalmentId)).stream()
            .findFirst()
            .map(CollectionPaymentMatchRepository.Balance::getAmount)
            .orElse(BigDecimal.ZERO);
    if (projection.isEmpty()
        || projection.get().isStale()
        || projection.get().isSourceMissing()
        || health.stale()
        || CollectionAmounts.outstanding(instalment.getAmount(), settled).signum() == 0) {
      throw incompatible("The transfer instalment must be open with current source evidence");
    }
    var offer =
        new Evidence(chequeId, instalmentId, actor, commands.now().truncatedTo(ChronoUnit.MICROS));
    offers.insert(tenant, offer);
    events.changed(instalmentId);
    return dto(offer);
  }

  static Offer dto(Evidence offer) {
    return new Offer(offer.chequeId(), offer.obligationId(), offer.actorId(), offer.recordedAt());
  }

  private static CollectionRuleException incompatible(String message) {
    return CollectionRuleException.conflict("COLLECTION_CHEQUE_OFFER_INCOMPATIBLE", message);
  }
}
