package com.fabricmanagement.notification.i18n.infra.repository;

import com.fabricmanagement.notification.i18n.domain.TranslationKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationKeyRepository extends JpaRepository<TranslationKey, UUID> {

  Optional<TranslationKey> findByKeyCode(String keyCode);

  boolean existsByKeyCode(String keyCode);

  @Query(
      """
      SELECT tk FROM TranslationKey tk
      WHERE tk.module = :module AND tk.isActive = true
      """)
  java.util.List<TranslationKey> findByModule(@Param("module") String module);
}
