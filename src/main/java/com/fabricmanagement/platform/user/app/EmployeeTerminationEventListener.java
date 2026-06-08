package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.human.core.employee.domain.event.EmployeeTerminatedEvent;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class EmployeeTerminationEventListener {

  private static final Logger log = LoggerFactory.getLogger(EmployeeTerminationEventListener.class);
  private final UserRepository userRepository;
  private final IdempotentEventHandler idempotentHandler;

  public EmployeeTerminationEventListener(
      UserRepository userRepository, IdempotentEventHandler idempotentHandler) {
    this.userRepository = userRepository;
    this.idempotentHandler = idempotentHandler;
  }

  @ApplicationModuleListener
  public void handleEmployeeTerminatedEvent(EmployeeTerminatedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "handleEmployeeTerminatedEvent",
        () -> {
          log.info("Handling EmployeeTerminatedEvent for user ID: {}", event.getUserId());

          userRepository
              .findById(event.getUserId())
              .ifPresent(
                  user -> {
                    log.info(
                        "Deactivating user account {} due to employee termination.", user.getUid());
                    user.delete(); // Soft delete sets isActive to false
                    userRepository.save(user);
                  });
        });
  }
}
