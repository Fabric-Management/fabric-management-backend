package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.TrustedDevice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

  Optional<TrustedDevice> findByDeviceHash(String deviceHash);

  @Query("SELECT COUNT(t) FROM TrustedDevice t WHERE t.userId = :userId")
  int countByUserId(@Param("userId") UUID userId);

  @Modifying
  @Query("DELETE FROM TrustedDevice t WHERE t.userId = :userId")
  void deleteByUserId(@Param("userId") UUID userId);
}
