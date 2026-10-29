package com.fabricmanagement.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.subscription.domain.Subscription;
import com.fabricmanagement.platform.subscription.domain.SubscriptionStatus;
import com.fabricmanagement.platform.subscription.dto.CreateInitialSubscriptionsResult;
import com.fabricmanagement.platform.subscription.dto.EvaluationPackageSelection;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal orchestration only: caller must establish the company's signup and registrant authority.
 */
@Service
@RequiredArgsConstructor
public class EvaluationSubscriptionService {
  private final EvaluationPackageQueryService catalogue;
  private final SubscriptionRepository subscriptions;
  private final TenantRepository tenants;
  private final EvaluationAccessInvalidator invalidator;

  @Transactional
  public CreateInitialSubscriptionsResult initialisePlayground(UUID tenantId) {
    Tenant tenant = lock(tenantId);
    if (!tenant.isDemoMode()
        || tenant.getStatus() != TenantStatus.TRIAL
        || !subscriptions.findByTenantId(tenantId).isEmpty()) throw unavailable();
    var offered = catalogue.lockedCatalogue();
    apply(tenant, offered, offered.codes(), "SERVER_DEFAULT");
    return CreateInitialSubscriptionsResult.builder()
        .osCodes(offered.codes())
        .trialEndsAt(null)
        .build();
  }

  @Transactional
  public void select(
      UUID tenantId, String intent, boolean pending, List<String> selected, String version) {
    Tenant tenant = lock(tenantId);
    List<Subscription> rows = subscriptions.findByTenantId(tenantId);
    if (!editable(tenant, intent, pending, rows)) throw unavailable();
    var offered = catalogue.lockedCatalogue();
    if (!Objects.equals(offered.version(), version)) {
      throw new PlatformDomainException(
              "Reload the available evaluation packages", "ONBOARDING_CATALOGUE_CONFLICT", 409)
          .withDetail("catalogueVersion", offered.version());
    }
    catalogue.requireSelection(offered, selected);
    apply(tenant, offered, selected, "OWNER");
  }

  @Transactional(readOnly = true)
  public EvaluationPackageSelection state(UUID tenantId, String intent, boolean pending) {
    var tenant = tenants.findById(tenantId).orElseThrow(EvaluationSubscriptionService::unavailable);
    var offered = catalogue.catalogue();
    var rows = subscriptions.findByTenantId(tenantId);
    boolean editable = editable(tenant, intent, pending, rows);
    String source =
        rows.stream().anyMatch(s -> "OWNER".equals(s.getEvaluationSelectionSource()))
            ? "OWNER"
            : rows.stream()
                .map(Subscription::getEvaluationSelectionSource)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    return new EvaluationPackageSelection(
        offered.version(),
        offered.definitions().stream()
            .map(
                d ->
                    new EvaluationPackageSelection.Option(
                        d.getOsCode(), d.getOsName(), "FabricOS".equals(d.getOsCode())))
            .toList(),
        rows.stream()
            .filter(
                s ->
                    s.getDeletedAt() == null
                        && Boolean.TRUE.equals(s.getIsActive())
                        && s.isActive())
            .map(Subscription::getOsCode)
            .sorted()
            .toList(),
        editable,
        editable ? null : "ONBOARDING_EVALUATION_UNAVAILABLE",
        source);
  }

  private void apply(
      Tenant tenant,
      EvaluationPackageQueryService.Catalogue offered,
      List<String> selected,
      String source) {
    var rows = subscriptions.findByTenantId(tenant.getId());
    for (var definition : offered.definitions()) {
      if (!selected.contains(definition.getOsCode())) continue;
      var row =
          rows.stream()
              .filter(s -> s.getOsCode().equals(definition.getOsCode()))
              .findFirst()
              .orElseGet(
                  () -> {
                    var created =
                        Subscription.builder()
                            .osCode(definition.getOsCode())
                            .osName(definition.getOsName())
                            .startDate(Instant.now())
                            .build();
                    created.setTenantId(tenant.getId());
                    return created;
                  });
      if (row.getDeletedAt() != null || !Boolean.TRUE.equals(row.getIsActive()))
        throw unavailable();
      row.selectEvaluation(tenant.isDemoMode(), tenant.getTrialEndsAt(), source);
      row.setPricingTier(definition.getDefaultTier());
      subscriptions.save(row);
    }
    for (var row : rows) {
      if (selected.contains(row.getOsCode())) continue;
      // Existing trial rows are adopted only in the guarded pending-company workflow.
      if (row.getStatus() == SubscriptionStatus.TRIAL) {
        row.setEvaluationManaged(true);
        row.deselectEvaluation();
      }
    }
    subscriptions.flush();
    invalidator.afterCommit();
  }

  private Tenant lock(UUID tenantId) {
    if (!Objects.equals(tenantId, TenantContext.requireTenantId())) throw unavailable();
    return tenants
        .lockEvaluationTenant(tenantId)
        .orElseThrow(EvaluationSubscriptionService::unavailable);
  }

  private boolean editable(Tenant tenant, String intent, boolean pending, List<Subscription> rows) {
    return Boolean.TRUE.equals(tenant.getIsActive())
        && tenant.getStatus() == TenantStatus.TRIAL
        && (("PLAYGROUND".equals(intent) && tenant.isDemoMode())
            || (("TRIAL".equals(intent) || "SALES_LED".equals(intent))
                && pending
                && !tenant.isDemoMode()
                && tenant.getTrialEndsAt() != null
                && tenant.getTrialEndsAt().isAfter(Instant.now())))
        && rows.stream()
            .noneMatch(
                s ->
                    s.getStatus() == SubscriptionStatus.ACTIVE
                        || s.getStatus() == SubscriptionStatus.CANCELLED
                        || (s.getStatus() == SubscriptionStatus.SUSPENDED
                            && !s.isEvaluationManaged()));
  }

  private static PlatformDomainException unavailable() {
    return new PlatformDomainException(
        "Evaluation package changes are unavailable for this tenant",
        "ONBOARDING_EVALUATION_UNAVAILABLE",
        409);
  }
}
