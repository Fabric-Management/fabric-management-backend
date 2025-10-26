package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.RegistrationTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RegistrationToken entity.
 */
@Repository
public interface RegistrationTokenRepository extends JpaRepository<RegistrationToken, UUID> {

    /**
     * Find token by token string.
     *
     * @param token Token string
     * @return Registration token if found
     */
    Optional<RegistrationToken> findByToken(String token);

    /**
     * Find valid token by contact value and type.
     *
     * @param contactValue Contact value
     * @param tokenType Token type
     * @param now Current timestamp
     * @return Valid token if found
     */
    Optional<RegistrationToken> findByContactValueAndTokenTypeAndIsUsedFalseAndExpiresAtAfter(
        String contactValue,
        RegistrationTokenType tokenType,
        Instant now
    );

    /**
     * Check if contact has any valid tokens.
     *
     * @param contactValue Contact value
     * @param now Current timestamp
     * @return true if valid token exists
     */
    boolean existsByContactValueAndIsUsedFalseAndExpiresAtAfter(String contactValue, Instant now);

    /**
     * Delete expired tokens (cleanup job).
     *
     * @param now Current timestamp
     */
    void deleteByExpiresAtBefore(Instant now);
}

