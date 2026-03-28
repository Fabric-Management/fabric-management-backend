package com.fabricmanagement.platform.tradingpartner.infra.repository;

import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerCertification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradingPartnerCertificationRepository
    extends JpaRepository<TradingPartnerCertification, UUID> {

  List<TradingPartnerCertification> findByTradingPartnerIdAndIsActiveTrue(UUID tradingPartnerId);

  List<TradingPartnerCertification> findByTradingPartnerId(UUID tradingPartnerId);
}
