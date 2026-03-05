package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.human.core.employee.domain.event.EmployeeTerminatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmployeeTerminationEventListener {

  private static final Logger log = LoggerFactory.getLogger(EmployeeTerminationEventListener.class);
  private final UserRepository userRepository;

  public EmployeeTerminationEventListener(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEmployeeTerminatedEvent(EmployeeTerminatedEvent event) {
    log.info("Handling EmployeeTerminatedEvent for user ID: {}", event.getUserId());

    userRepository
        .findById(event.getUserId())
        .ifPresent(
            user -> {
              log.info("Deactivating user account {} due to employee termination.", user.getUid());
              user.delete(); // Soft delete sets isActive to false
              userRepository.save(user);
            });
  }
}
