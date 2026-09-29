package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared ISO code reference data. {@code baseOnly=true} keeps the official-code filter, scoped to
 * the catalogue owner like every other ISO read (FIBER-CATALOG-1).
 */
@Service
@RequiredArgsConstructor
public class FiberIsoCodeService {

  private final FiberReferenceQueryService referenceQueryService;

  public List<FiberIsoCodeDto> getIsoCodes(boolean baseOnly) {
    return referenceQueryService.listIsoCodes(baseOnly);
  }
}
