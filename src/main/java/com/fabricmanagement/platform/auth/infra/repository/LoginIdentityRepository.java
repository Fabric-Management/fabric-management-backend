package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginIdentityRepository extends JpaRepository<LoginIdentity, UUID> {

  Optional<LoginIdentity> findByEmail(String email);

  boolean existsByEmail(String email);
}
