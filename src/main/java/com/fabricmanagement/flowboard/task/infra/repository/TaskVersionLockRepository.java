package com.fabricmanagement.flowboard.task.infra.repository;

import com.fabricmanagement.flowboard.task.domain.Task;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Consumes the version of an already managed Task even when only its child rows change. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class TaskVersionLockRepository {

  @PersistenceContext private EntityManager entityManager;

  public void forceIncrement(Task task) {
    // Lock the managed instance explicitly: a query lock upgrade on an already loaded
    // entity does not register Hibernate's deferred force-increment operation.
    entityManager.lock(task, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
  }
}
