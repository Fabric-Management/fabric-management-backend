package com.fabricmanagement.identity.infrastructure.persistence.repository;

import com.fabricmanagement.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for UserEntity.
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);

    @Query("SELECT u FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :contactValue")
    Optional<UserEntity> findByContactValue(@Param("contactValue") String contactValue);

    boolean existsByUsername(String username);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :contactValue")
    boolean existsByContactValue(@Param("contactValue") String contactValue);
}