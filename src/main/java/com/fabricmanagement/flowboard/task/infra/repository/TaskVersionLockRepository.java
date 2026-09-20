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

  /**
   * Deferred increment, applied by Hibernate when the transaction completes. This is the locked
   * FE-ARCH-5b-1 / 5b-1b behaviour and stays the mechanism for callers that do not report the new
   * version (routing evaluation).
   */
  public void forceIncrement(Task task) {
    // Lock the managed instance explicitly: a query lock upgrade on an already loaded
    // entity does not register Hibernate's deferred force-increment operation.
    entityManager.lock(task, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
  }

  /**
   * Immediate increment for the transition orchestrator (FE-ARCH-5b-3 §4.2), which must return the
   * task's current version in its response. The deferred variant cannot supply it: its increment
   * runs at transaction completion, after the response has been built. PESSIMISTIC_FORCE_INCREMENT
   * issues the versioned UPDATE now, keeps the managed entity and the row in step, and holds the
   * row lock for the rest of the transaction; the wait is bounded by the lock timeout the
   * orchestrator sets as its first statement. A concurrent version change surfaces as an
   * optimistic-locking failure, which the orchestrator reports as TASK_VERSION_CONFLICT.
   */
  public long forceIncrementNow(Task task) {
    entityManager.flush();
    entityManager.lock(task, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    return task.getVersion();
  }
}
