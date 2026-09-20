package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.TaskAffectedSubject;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAffectedSubjectRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes the authoritative unresolved subject set while retaining row history. */
@Service
@RequiredArgsConstructor
public class TaskAffectedSubjectService {

  private final TaskAffectedSubjectRepository repository;

  public void synchronize(UUID taskId, Set<TaskSubject> authoritative) {
    Map<TaskSubject, TaskAffectedSubject> existing = new HashMap<>();
    repository
        .findAllByTaskId(taskId)
        .forEach(
            row -> existing.put(new TaskSubject(row.getSubjectType(), row.getSubjectId()), row));

    existing.forEach(
        (subject, row) -> {
          if (authoritative.contains(subject)) {
            row.activate();
          } else if (Boolean.TRUE.equals(row.getIsActive())) {
            row.delete();
          }
        });
    authoritative.stream()
        .filter(subject -> !existing.containsKey(subject))
        .map(subject -> TaskAffectedSubject.create(taskId, subject.type(), subject.id()))
        .forEach(repository::save);
  }

  @Transactional(readOnly = true)
  public Set<TaskSubject> current(UUID taskId) {
    return repository.findAllByTaskId(taskId).stream()
        .filter(row -> Boolean.TRUE.equals(row.getIsActive()))
        .map(row -> new TaskSubject(row.getSubjectType(), row.getSubjectId()))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
