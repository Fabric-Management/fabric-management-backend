package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.event.CollectionObligationChangedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionEvents {
  private final DomainEventPublisher publisher;

  public void changed(UUID id) {
    publisher.publish(new CollectionObligationChangedEvent(TenantContext.requireTenantId(), id));
  }
}
