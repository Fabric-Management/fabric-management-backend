package com.fabricmanagement.platform.communication.infra.repository;

import com.fabricmanagement.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.platform.communication.domain.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for EmailOutbox entity. */
@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

  /**
   * Find pending emails ready to be sent (for background job).
   *
   * <p>Returns emails that are:
   *
   * <ul>
   *   <li>PENDING status
   *   <li>Retry count < max retries
   *   <li>Next retry time has passed (or null)
   * </ul>
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
  @Query(
      "SELECT e FROM EmailOutbox e "
          + "WHERE e.status = :status "
          + "AND e.retryCount < e.maxRetries "
          + "AND e.isActive = true "
          + "AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) "
          + "ORDER BY e.createdAt ASC")
  List<EmailOutbox> findPendingEmailsReadyForSending(
      @Param("status") EmailOutboxStatus status, @Param("now") Instant now);

  /** Find permanently failed emails (dead letter queue). */
  List<EmailOutbox> findByStatusAndIsActiveTrueOrderByCreatedAtDesc(EmailOutboxStatus status);

  /** Count emails by status (for monitoring). */
  long countByStatusAndIsActiveTrue(EmailOutboxStatus status);
}
