package com.fabricmanagement.platform.subscription.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.subscription.app.SubscriptionQuotaService;
import com.fabricmanagement.platform.subscription.app.SubscriptionService;
import com.fabricmanagement.platform.subscription.dto.SubscriptionDto;
import com.fabricmanagement.platform.subscription.dto.SubscriptionQuotaDto;
import com.fabricmanagement.platform.subscription.dto.UpdateSubscriptionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/common/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscription", description = "Subscription operations")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;
  private final SubscriptionQuotaService quotaService;

  @GetMapping("/organization/{organizationId}")
  public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getOrganizationSubscriptions(
      @PathVariable UUID organizationId) {
    log.debug("Getting subscriptions for organization: organizationId={}", organizationId);
    List<SubscriptionDto> subscriptions =
        subscriptionService.getOrganizationSubscriptions(organizationId);
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
