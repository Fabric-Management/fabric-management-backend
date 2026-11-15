package com.fabricmanagement.platform.tradingpartner.infra.repository;

import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingPartnerChequeDrawerRepository
    extends JpaRepository<TradingPartnerChequeDrawer, UUID> {

  List<TradingPartnerChequeDrawer> findByTenantIdAndPartnerIdOrderByNameAscIdAsc(
      UUID tenantId, UUID partnerId);

  List<TradingPartnerChequeDrawer> findByTenantIdAndPartnerIdAndIsActiveTrueOrderByNameAscIdAsc(
      UUID tenantId, UUID partnerId);

  Optional<TradingPartnerChequeDrawer> findByTenantIdAndPartnerIdAndNormalizedName(
      UUID tenantId, UUID partnerId, String normalizedName);

  Optional<TradingPartnerChequeDrawer> findByTenantIdAndPartnerIdAndId(
      UUID tenantId, UUID partnerId, UUID id);
}
