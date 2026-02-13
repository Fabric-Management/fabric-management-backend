package com.fabricmanagement.common.platform.subscription.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.subscription.app.SubscriptionQuotaService;
import com.fabricmanagement.common.platform.subscription.app.SubscriptionService;
import com.fabricmanagement.common.platform.subscription.dto.SubscriptionDto;
import com.fabricmanagement.common.platform.subscription.dto.SubscriptionQuotaDto;
import com.fabricmanagement.common.platform.subscription.dto.UpdateSubscriptionRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Subscription management — separate from company CRUD.
 *
 * <p>Base path: /api/common/subscriptions.
 */
@RestController
@RequestMapping("/api/common/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

  private final SubscriptionService subscriptionService;
  private final SubscriptionQuotaService quotaService;

  @GetMapping("/company/{companyId}")
  public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getCompanySubscriptions(
      @PathVariable UUID companyId) {
    log.debug("Getting subscriptions for company: companyId={}", companyId);
    List<SubscriptionDto> subscriptions = subscriptionService.getCompanySubscriptions(companyId);
    return ResponseEntity.ok(ApiResponse.success(subscriptions));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getActiveSubscriptions() {
    log.debug("Getting active subscriptions");
    List<SubscriptionDto> subscriptions = subscriptionService.getActiveSubscriptions();
    return ResponseEntity.ok(ApiResponse.success(subscriptions));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(@PathVariable UUID id) {
    log.debug("Getting subscription: id={}", id);
    SubscriptionDto subscription = subscriptionService.getSubscription(id);
    return ResponseEntity.ok(ApiResponse.success(subscription));
  }

  @PostMapping("/{id}/activate")
  public ResponseEntity<ApiResponse<SubscriptionDto>> activateSubscription(@PathVariable UUID id) {
    log.info("Activating subscription: id={}", id);
    SubscriptionDto activated = subscriptionService.activateSubscription(id);
    return ResponseEntity.ok(ApiResponse.success(activated, "Subscription activated successfully"));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<SubscriptionDto>> updateSubscription(
      @PathVariable UUID id, @Valid @RequestBody UpdateSubscriptionRequest request) {
    log.info("Updating subscription: id={}", id);
    SubscriptionDto updated = subscriptionService.updateSubscription(id, request);
    return ResponseEntity.ok(ApiResponse.success(updated, "Subscription updated successfully"));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<SubscriptionDto>> cancelSubscription(@PathVariable UUID id) {
    log.info("Cancelling subscription: id={}", id);
    SubscriptionDto cancelled = subscriptionService.cancelSubscription(id);
    return ResponseEntity.ok(ApiResponse.success(cancelled, "Subscription cancelled successfully"));
  }

  @GetMapping("/{id}/quotas")
  public ResponseEntity<ApiResponse<List<SubscriptionQuotaDto>>> getSubscriptionQuotas(
      @PathVariable UUID id) {
    log.debug("Getting quotas for subscription: id={}", id);
    List<SubscriptionQuotaDto> quotas = quotaService.getSubscriptionQuotas(id);
    return ResponseEntity.ok(ApiResponse.success(quotas));
  }

  @GetMapping("/quotas")
  public ResponseEntity<ApiResponse<List<SubscriptionQuotaDto>>> getTenantQuotas() {
    log.debug("Getting quotas for current tenant");
    List<SubscriptionQuotaDto> quotas = quotaService.getTenantQuotas();
    return ResponseEntity.ok(ApiResponse.success(quotas));
  }

  @PutMapping("/{id}/quotas/{quotaType}/reset")
  public ResponseEntity<ApiResponse<SubscriptionQuotaDto>> resetQuota(
      @PathVariable UUID id, @PathVariable String quotaType) {
    log.info("Resetting quota: subscriptionId={}, quotaType={}", id, quotaType);
    SubscriptionQuotaDto reset = quotaService.resetQuota(id, quotaType);
    return ResponseEntity.ok(ApiResponse.success(reset, "Quota reset successfully"));
  }
}
