package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Task F1: Service for ISO code reference data with baseOnly filter.
 *
 * <p>When baseOnly=true, returns only official ISO 2076 codes (52 records). When false, returns all
 * active codes including variants.
 */
@Service
@RequiredArgsConstructor
public class FiberIsoCodeService {

  private final FiberIsoCodeRepository fiberIsoCodeRepository;

  public List<FiberIsoCodeDto> getIsoCodes(boolean baseOnly) {
    UUID tenantId =
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.requireTenantId();
    List<FiberIsoCode> codes =
        baseOnly
            ? fiberIsoCodeRepository.findByIsOfficialIsoTrueAndIsActiveTrue()
            : fiberIsoCodeRepository.findByTenantIdAndIsActiveTrue(tenantId);
    return codes.stream().map(FiberIsoCodeDto::from).toList();
  }
}
