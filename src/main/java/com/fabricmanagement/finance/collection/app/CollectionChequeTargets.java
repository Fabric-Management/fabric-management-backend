package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository.Balance;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService.Reach;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CollectionChequeTargets {
  private final CollectionPaymentMatchRepository matches;
  private final CollectionChequeApplicationRepository applications;
  private final CollectionProjectionRepository projections;

  Map<UUID, Facts> load(UUID tenant, List<CollectionObligation> rows) {
    if (rows.isEmpty()) return Map.of();
    var ids = rows.stream().map(CollectionObligation::getId).toList();
    var received = balances(applications.received(tenant, ids));
    var cash = balances(matches.nonChequeSettledFor(tenant, ids));
    var settled = balances(matches.settled(tenant, ids));
    var readableOrders =
        projections
            .findByTenantIdAndOrderIdIn(
                tenant, rows.stream().map(CollectionObligation::getOrderId).distinct().toList())
            .stream()
            .filter(row -> !row.isSourceMissing())
            .map(CollectionProjection::getOrderId)
            .collect(Collectors.toSet());
    return rows.stream()
        .collect(
            Collectors.toMap(
                CollectionObligation::getId,
                row ->
                    new Facts(
                        readableOrders.contains(row.getOrderId()),
                        amount(settled, row),
                        amount(received, row),
                        amount(cash, row))));
  }

  static boolean permits(Reach reach, CollectionObligation row) {
    return reach.permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
  }

  private static BigDecimal amount(Map<UUID, BigDecimal> amounts, CollectionObligation row) {
    return amounts.getOrDefault(row.getId(), BigDecimal.ZERO);
  }

  private static Map<UUID, BigDecimal> balances(List<Balance> values) {
    return values.stream().collect(Collectors.toMap(Balance::getId, Balance::getAmount));
  }

  record Facts(boolean sourceReadable, BigDecimal settled, BigDecimal received, BigDecimal cash) {
    boolean eligible(CollectionObligation row, Reach read, Reach write) {
      return permits(read, row) && permits(write, row) && acceptsCheque(row);
    }

    boolean acceptsCheque(CollectionObligation row) {
      return Boolean.TRUE.equals(row.getIsActive())
          && !row.isCancelled()
          && sourceReadable
          && row.getMethod() == Method.CHEQUE
          && settled.compareTo(row.getAmount()) < 0
          && lacking(row).signum() > 0;
    }

    BigDecimal lacking(CollectionObligation row) {
      return row.getAmount().subtract(received).subtract(cash).max(BigDecimal.ZERO);
    }
  }
}
