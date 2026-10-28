package com.fabricmanagement.flowboard.task.app.adapter;

import com.fabricmanagement.flowboard.collection.app.CollectionFollowUpService;
import com.fabricmanagement.flowboard.collection.dto.CollectionFollowUpDtos.*;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.TaskActionPayload;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Typed non-financial actions retain the task's unresolved subject. They deliberately use the
 * follow-up command receipt rather than an accepted terminal Task transition: calling a customer
 * cannot complete the collection. The service verifies the pin, owner and all three versions.
 */
@Component
@RequiredArgsConstructor
public class CollectionFollowUpActionAdapter {
  private final CollectionFollowUpService followUps;

  public Result execute(UUID followUpId, UUID actor, TaskActionPayload payload) {
    return switch (payload) {
      case ContactRequest contact -> followUps.recordContact(followUpId, actor, contact);
      case AssignRequest assignment -> followUps.assign(followUpId, actor, assignment);
      default ->
          throw new FlowBoardDomainException(
              "Unsupported collection follow-up action", "COLLECTION_ACTION_UNSUPPORTED", 400);
    };
  }
}
