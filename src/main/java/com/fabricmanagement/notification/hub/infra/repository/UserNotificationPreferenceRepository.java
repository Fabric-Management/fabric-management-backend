package com.fabricmanagement.notification.hub.infra.repository;

import com.fabricmanagement.notification.hub.domain.UserNotificationPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationPreferenceRepository
    extends JpaRepository<UserNotificationPreference, UUID> {

  Optional<UserNotificationPreference> findByUserIdAndEventType(UUID userId, String eventType);
}
