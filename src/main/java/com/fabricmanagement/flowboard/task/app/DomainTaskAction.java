package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;

/** Typed domain adapter invoked inside the caller-owned Task transaction. */
public interface DomainTaskAction {
  String actionKey();

  boolean supports(Task task);

  DomainTaskActionResult execute(Task task, TaskActionCommand command);
}
