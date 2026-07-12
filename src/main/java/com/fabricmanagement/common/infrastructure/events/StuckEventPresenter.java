package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface StuckEventPresenter {

  boolean supports(String eventType);

  /** Called inside an active tenant context with the database session already bound. */
  StuckEventPresentation present(UUID tenantId, JsonNode payload);
}
