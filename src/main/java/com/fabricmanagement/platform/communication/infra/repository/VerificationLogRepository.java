package com.fabricmanagement.platform.communication.infra.repository;

import com.fabricmanagement.platform.communication.domain.DeliveryStatus;
import com.fabricmanagement.platform.communication.domain.VerificationLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, UUID> {

  /** Find pending WhatsApp checks older than a specific time to trigger timeouts */
  @Query(
      "SELECT v FROM VerificationLog v WHERE v.deliveryStatus = 'PENDING' AND v.deliveryChannel = 'WHATSAPP' AND v.createdAt <= :timeoutThreshold")
  List<VerificationLog> findPendingWhatsAppMessages(
      @Param("timeoutThreshold") Instant timeoutThreshold);

  /** Updates status based on external WAMID matching */
  @Modifying
  @Query(
      "UPDATE VerificationLog v SET v.deliveryStatus = :status WHERE v.externalMessageId = :wamid")
  int updateStatusByExternalMessageId(
      @Param("wamid") String wamid, @Param("status") DeliveryStatus status);
}
