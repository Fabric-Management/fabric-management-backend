package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverActivationRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal cutover primitive. It is deliberately not exposed by an HTTP route or seed. */
@Service
@RequiredArgsConstructor
public class OrderCoverActivationService {
  private final OrderCoverActivationRepository activations;

  @Transactional
  public OrderCoverActivationRepository.Activation activate() {
    return activations.activateOnce(
        TenantContext.requireTenantId(), Objects.requireNonNull(TenantContext.getCurrentUserId()));
  }
}
