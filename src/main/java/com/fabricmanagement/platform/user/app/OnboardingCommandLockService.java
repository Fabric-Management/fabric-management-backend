package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serialises completion, including the personal-only path, on the persisted membership. */
@Service
@RequiredArgsConstructor
public class OnboardingCommandLockService {
  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockAndRefresh(User user) {
    // Lock the row first, then read it. A locking refresh of User (joined associations) makes
    // Hibernate read unlocked and then lock "where version = <what it read>": the command that
    // waited for another one to commit then fails as stale instead of seeing the completion.
    entityManager
        .createNativeQuery("SELECT 1 FROM common_user.common_user WHERE id = :id FOR UPDATE")
        .setParameter("id", user.getId())
        .getResultList();
    // The access policy already loaded this entity; refreshing under the held lock replaces its
    // stale first-level-cache completion marker with what the previous command committed.
    entityManager.refresh(user);
    if (user.getRole() != null) entityManager.refresh(user.getRole());
    user.getUserDepartments().stream()
        .map(com.fabricmanagement.platform.user.domain.UserDepartment::getDepartment)
        .filter(java.util.Objects::nonNull)
        .forEach(entityManager::refresh);
  }
}
