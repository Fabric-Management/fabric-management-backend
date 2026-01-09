package com.fabricmanagement.human.compliance.localization.infra.repository;

import com.fabricmanagement.human.compliance.localization.domain.HrCountryPackMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HrCountryPackMappingRepository extends JpaRepository<HrCountryPackMapping, UUID> {

  @Query(
      """
        select m from HrCountryPackMapping m
        where m.tenantId = :tenantId
          and m.countryCode = :countryCode
        """)
  Optional<HrCountryPackMapping> findByCountryCode(
      @Param("tenantId") UUID tenantId, @Param("countryCode") String countryCode);

  @Query(
      """
        select m from HrCountryPackMapping m
        where m.tenantId = :tenantId
        """)
  List<HrCountryPackMapping> findAllByTenant(@Param("tenantId") UUID tenantId);

  @Query(
      """
        select m from HrCountryPackMapping m
        where m.tenantId = :tenantId
          and m.packCode = :packCode
        """)
  List<HrCountryPackMapping> findByPackCode(
      @Param("tenantId") UUID tenantId, @Param("packCode") String packCode);
}
