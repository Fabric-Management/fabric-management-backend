package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStep;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeMetres;
import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the firm should charge for a lot that is out (FABRIC-3c-2b), worked out from the finishing
 * price list when the metres come back. It only suggests: the cost on the step is whatever the user
 * confirms when receiving, since the firm's invoice is the truth and a difference from the list is
 * worth noticing, not hiding.
 */
@Service
@RequiredArgsConstructor
public class BatchFinishingCostService {

  private final BatchFinishingStepRepository stepRepository;
  private final BatchFinishingService finishing;
  private final FinishingStepGradeService stepGrades;

  /**
   * Prices a delivery that has not been received yet. The prices are read for the given day, by
   * default the day the lot was sent, the day the job was placed at the firm's tariff of that day.
   */
  @Transactional(readOnly = true)
  public BatchFinishingDtos.CostSuggestionDto suggest(
      UUID stepId, BatchFinishingDtos.CostSuggestionRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    BatchFinishingStep step =
        stepRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, stepId)
            .orElseThrow(() -> new NotFoundException("Finishing step not found: " + stepId));
    if (!step.isOut()) {
      throw new BatchDomainException(
          "A finishing step in status "
              + step.getStatus()
              + " is not out; there is nothing to price",
          "FINISHING_STEP_NOT_OUT",
          422);
    }
    FinishingGradeBreakdown breakdown =
        stepGrades.checked(request.grades(), request.returnedMetres());
    LocalDate priceDate = request.priceDate() != null ? request.priceDate() : step.getSentOn();
    List<FabricFinishingPriceDto> prices =
        finishing.prices(
            step.getBatchId(),
            step.getProcessId(),
            step.getPartnerId(),
            step.getPriceClassId(),
            priceDate);
    return FinishingCostSuggester.suggest(
        priceDate,
        step.getSentMetres(),
        request.returnedMetres(),
        breakdown,
        prices,
        stepGrades.references(
            breakdown.rows().stream().map(FinishingGradeMetres::qualityGradeId).toList()));
  }
}
