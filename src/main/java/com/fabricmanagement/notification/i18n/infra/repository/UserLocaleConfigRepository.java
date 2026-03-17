package com.fabricmanagement.notification.i18n.infra.repository;

import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLocaleConfigRepository extends JpaRepository<UserLocaleConfig, UUID> {

  Optional<UserLocaleConfig> findByUserId(UUID userId);
}
