package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.task.domain.TaskAffectedSubject;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAffectedSubjectRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAffectedSubjectServiceTest {

  @Mock private TaskAffectedSubjectRepository repository;

  @Test
  void removesStaleScopeAndAddsNewAuthoritativeSubject() {
    UUID taskId = UUID.randomUUID();
    TaskAffectedSubject stale =
        TaskAffectedSubject.create(taskId, "SALES_ORDER_LINE", UUID.randomUUID());
    TaskSubject current = new TaskSubject("SALES_ORDER_LINE", UUID.randomUUID());
    when(repository.findAllByTaskId(taskId)).thenReturn(List.of(stale));

    new TaskAffectedSubjectService(repository).synchronize(taskId, Set.of(current));

    assertThat(stale.getIsActive()).isFalse();
    ArgumentCaptor<TaskAffectedSubject> added = ArgumentCaptor.forClass(TaskAffectedSubject.class);
    verify(repository).save(added.capture());
    assertThat(added.getValue().getSubjectType()).isEqualTo(current.type());
    assertThat(added.getValue().getSubjectId()).isEqualTo(current.id());
  }
}
