package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.approval.app.ApprovalPolicyService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.flowboard.board.app.BoardService;
import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.dto.CreateBoardRequest;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class E2EShowcaseSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final ExchangeRateService exchangeRateService;
  private final ApprovalPolicyService approvalPolicyService;
  private final BoardService boardService;
  private final BoardRepository boardRepository;
  private final TransactionTemplate transactionTemplate;

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> tenantOpt = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (tenantOpt.isEmpty()) {
      return false;
    }

    return TenantContext.executeInTenantContext(
        tenantOpt.get().getId(),
        () -> {
          UUID tenantId = tenantOpt.get().getId();

          // Granular check: verify each domain independently
          boolean hasWorkOrderPolicy =
              approvalPolicyService
                  .getActivePolicyFor(tenantId, ApprovalEntityType.WORK_ORDER)
                  .isPresent();
          boolean hasRecipePolicy =
              approvalPolicyService
                  .getActivePolicyFor(tenantId, ApprovalEntityType.RECIPE_CREATE)
                  .isPresent();
          boolean hasUsdRate =
              exchangeRateService.getRate(tenantId, "USD", "TRY", LocalDate.now()).isPresent();
          boolean hasEurRate =
              exchangeRateService.getRate(tenantId, "EUR", "TRY", LocalDate.now()).isPresent();
          boolean hasBoard =
              boardRepository.findByTenantIdAndBoardType(tenantId, BoardType.GLOBAL).isPresent();

          return hasWorkOrderPolicy && hasRecipePolicy && hasUsdRate && hasEurRate && hasBoard;
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(() -> new IllegalStateException("Tenant must be seeded before Showcase"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                UUID tenantId = tenant.getId();

                // 1. Costing & Finance: Exchange Rates (saveRate already has saveOrUpdate
                // semantics)
                exchangeRateService.saveRate(
                    "USD",
                    "TRY",
                    BigDecimal.valueOf(35.50),
                    LocalDate.now(),
                    ExchangeRateSource.MANUAL);
                exchangeRateService.saveRate(
                    "EUR",
                    "TRY",
                    BigDecimal.valueOf(38.20),
                    LocalDate.now(),
                    ExchangeRateSource.MANUAL);
                log.info("Exchange Rates Seeded.");

                // 2. Approval Policies — per-entity-type idempotency
                if (approvalPolicyService
                    .getActivePolicyFor(tenantId, ApprovalEntityType.WORK_ORDER)
                    .isEmpty()) {
                  approvalPolicyService.createPolicy(
                      tenantId,
                      ApprovalEntityType.WORK_ORDER,
                      PolicyTargetLevel.ALL,
                      ApproverRole.TENANT_ADMIN,
                      10,
                      48);
                  log.info("Created WORK_ORDER approval policy.");
                }

                if (approvalPolicyService
                    .getActivePolicyFor(tenantId, ApprovalEntityType.RECIPE_CREATE)
                    .isEmpty()) {
                  approvalPolicyService.createPolicy(
                      tenantId,
                      ApprovalEntityType.RECIPE_CREATE,
                      PolicyTargetLevel.STANDARD,
                      ApproverRole.DEPARTMENT_MANAGER,
                      5,
                      48);
                  log.info("Created RECIPE_CREATE approval policy.");
                }

                // 3. Flowboard Setup — per-type idempotency
                if (boardRepository
                    .findByTenantIdAndBoardType(tenantId, BoardType.GLOBAL)
                    .isEmpty()) {
                  CreateBoardRequest boardReq =
                      new CreateBoardRequest(
                          "Production Flow",
                          BoardType.GLOBAL,
                          5,
                          null,
                          "Showcase tasks and workflows");
                  boardService.createBoard(boardReq);
                  log.info("Flowboard Seeded.");
                }
              });
        });
  }

  @Override
  public int getOrder() {
    return 99; // Final E2E Scenario setups
  }
}
