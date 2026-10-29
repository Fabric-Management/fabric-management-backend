package com.fabricmanagement.flowboard.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import java.util.*;
import org.junit.jupiter.api.Test;

class CollectionTaskGovernanceTest {
  @Test
  void legacyAssignmentAndCancellationCannotBypassTheCollectionAction() {
    var id = UUID.randomUUID();
    var actor = UUID.randomUUID();
    var repository = mock(TaskRepository.class);
    var assignees = mock(TaskAssigneeRepository.class);
    var task =
        Task.create(
            "TSK-0042",
            UUID.randomUUID(),
            "Collection follow-up",
            TaskType.COLLECTION_FOLLOW_UP,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            "COLLECTION_OBLIGATION",
            UUID.randomUUID());
    task.setId(id);
    task.govern("COLLECTION_FOLLOW_UP:test:1", TaskWorkflowRegistry.COLLECTION_DEFINITION_ID, 1);
    when(repository.findById(id)).thenReturn(Optional.of(task));
    when(repository.findByIdForAssignmentUpdate(id)).thenReturn(Optional.of(task));
    var service =
        new TaskService(
            repository,
            assignees,
            mock(TaskLabelAssignmentRepository.class),
            mock(TaskLabelRepository.class),
            mock(DomainEventPublisher.class),
            mock(UserFacade.class),
            mock(TaskProvisioningService.class),
            new TaskWorkflowRegistry());
    assertThatThrownBy(() -> service.assignToUser(id, actor, AssignedBy.MANAGER, actor))
        .hasMessageContaining("governed routing");
    assertThatThrownBy(() -> service.unassignUser(id, actor, actor))
        .hasMessageContaining("governed routing");
    assertThatThrownBy(() -> service.cancelTask(id)).hasMessageContaining("typed domain action");
    verify(assignees, never()).save(any());
    assertThat(task.isOpen()).isTrue();
  }
}
