package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cross-module okuma servisi — production modülünün TradingPartnerCertificationRepository infra
 * katmanına doğrudan erişmesini önler (Rule 13.1).
 */
@Service
@RequiredArgsConstructor
public class TradingPartnerCertificationQueryService {

  private final TradingPartnerCertificationRepository repository;

  public Optional<TradingPartnerCertification> findById(UUID partnerCertId) {
    return repository.findById(partnerCertId);
  }
}
