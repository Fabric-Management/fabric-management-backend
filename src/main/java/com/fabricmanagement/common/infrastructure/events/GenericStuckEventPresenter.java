package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericStuckEventPresenter implements StuckEventPresenter {

  @Override
  public boolean supports(String eventType) {
    return true;
  }

  @Override
  public StuckEventPresentation present(UUID tenantId, JsonNode payload) {
    return new StuckEventPresentation(
        "UNKNOWN", null, null, "A background follow-up did not complete.", null, null, null);
  }
}
