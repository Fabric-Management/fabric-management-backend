package com.fabricmanagement.notification.i18n.infra.repository;

import com.fabricmanagement.notification.i18n.domain.TranslationValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TranslationValueRepository extends JpaRepository<TranslationValue, UUID> {

  @Query(
      """
      SELECT tv FROM TranslationValue tv
      WHERE tv.translationKey.keyCode = :keyCode
        AND tv.locale = :locale
        AND tv.tenantId = :tenantId
        AND tv.isActive = true
      ORDER BY tv.isOverride DESC
      """)
  Optional<TranslationValue> findByKeyCodeAndLocaleAndTenant(
      @Param("keyCode") String keyCode,
      @Param("locale") String locale,
      @Param("tenantId") UUID tenantId);

  @Query(
      """
      SELECT tv FROM TranslationValue tv
      WHERE tv.translationKey.keyCode = :keyCode
        AND tv.locale = :locale
        AND tv.isActive = true
        AND tv.isOverride = false
      """)
  Optional<TranslationValue> findSystemDefault(
      @Param("keyCode") String keyCode, @Param("locale") String locale);

  @Query(
      """
      SELECT tv FROM TranslationValue tv
      WHERE tv.translationKey.id = :keyId AND tv.isActive = true
      """)
  List<TranslationValue> findAllByTranslationKeyId(@Param("keyId") UUID keyId);
}
