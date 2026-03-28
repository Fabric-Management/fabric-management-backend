package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.platform.audit.app.AuditService;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerCreatedEvent;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerLinkedEvent;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerRegistryCreatedEvent;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Event listener for trading partner domain events.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Audit logging for partner and registry events
 *   <li>Notifications when partner is linked to platform tenant
 *   <li>Future: Portal notifications, integration triggers
 * </ul>
 *
 * <h2>Event Flow:</h2>
 *
 * <pre>
 * TradingPartnerCreatedEvent     → Audit log + future notifications
 * TradingPartnerRegistryCreatedEvent → Platform audit log
 * TradingPartnerLinkedEvent      → Notify affected tenants (cross-tenant)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerEventListener {

  private final AuditService auditService;

  /**
   * Handle trading partner created event.
   *
   * <p>Logs the event for audit purposes. In future phases, this can trigger:
   *
   * <ul>
   *   <li>Integration notifications (ERP sync)
   *   <li>Welcome email to partner contact
   *   <li>Dashboard widget update
   * </ul>
   */
  @EventListener
  public void onTradingPartnerCreated(TradingPartnerCreatedEvent event) {
    log.info(
        "[AUDIT] TradingPartner created: id={}, registryId={}, type={}, tenant={}, displayName={}",
        event.getTradingPartnerId(),
        event.getRegistryId(),
        event.getPartnerType(),
        event.getTenantId(),
        event.getDisplayName());

    auditService.logAction(
        "TRADING_PARTNER_CREATED",
        "trading_partner",
        event.getTradingPartnerId().toString(),
        String.format(
            "Partner created: name=%s, type=%s, registryId=%s",
            event.getDisplayName(), event.getPartnerType(), event.getRegistryId()));

    // Check if this is a migrated partner (has legacy ID)
    if (event.getLegacyCompanyId() != null) {
      log.debug(
          "[MIGRATION] Partner {} migrated from Company {}",
          event.getTradingPartnerId(),
          event.getLegacyCompanyId());
    }
  }

  /**
   * Handle registry created event.
   *
   * <p>Platform-level audit. No tenant context.
   */
  @EventListener
  public void onTradingPartnerRegistryCreated(TradingPartnerRegistryCreatedEvent event) {
    log.info(
        "[AUDIT] TradingPartnerRegistry created: id={}, taxId={}, name={}, country={}",
        event.getRegistryId(),
        event.getTaxId(),
        event.getOfficialName(),
        event.getCountry());

    auditService.logAction(
        "TRADING_PARTNER_REGISTRY_CREATED",
        "trading_partner_registry",
        event.getRegistryId().toString(),
        String.format(
            "Registry created: name=%s, taxId=%s, country=%s",
            event.getOfficialName(), event.getTaxId(), event.getCountry()));
  }

  /**
   * Handle partner linked to tenant event.
   *
   * <p>This is a cross-tenant event - notifies all affected tenants that a partner they work with
   * is now on the platform.
   *
   * <h2>Future Features:</h2>
   *
   * <ul>
   *   <li>Send notification to affected tenant admins
   *   <li>Update UI badges ("Partner is on platform")
   *   <li>Enable cross-tenant features (shared documents, order visibility)
   * </ul>
   */
  @EventListener
  @Async // Non-blocking - notifications can be slow
  public void onTradingPartnerLinked(TradingPartnerLinkedEvent event) {
    log.info(
        "[AUDIT] TradingPartnerRegistry linked to tenant: registryId={}, linkedTenantId={}, affectedTenants={}",
        event.getRegistryId(),
        event.getLinkedTenantId(),
        event.getAffectedTenantIds().size());

    auditService.logAction(
        "TRADING_PARTNER_LINKED",
        "trading_partner_registry",
        event.getRegistryId().toString(),
        String.format(
            "Registry linked to tenant %s, %d affected tenants",
            event.getLinkedTenantId(), event.getAffectedTenantIds().size()));

    // Notify each affected tenant
    for (var tenantId : event.getAffectedTenantIds()) {
      log.debug(
          "[NOTIFICATION] Tenant {} will be notified: partner linked (registry={})",
          tenantId,
          event.getRegistryId());
      // NOTE: NotificationService entegrasyonu ileride eklenecek — şimdilik audit kaydı yeterli
    }
  }

  /**
   * Handle partner status change event.
   *
   * <p>Logs the status transition for audit purposes. In future phases, this can trigger:
   *
   * <ul>
   *   <li>Notification to affected users when partner is suspended/blocked
   *   <li>Cascading status changes to related orders/invoices
   *   <li>Compliance workflow triggers
   * </ul>
   */
  @EventListener
  public void onTradingPartnerStatusChanged(TradingPartnerStatusChangedEvent event) {
    log.info(
        "[AUDIT] TradingPartner status changed: id={}, from={}, to={}, tenant={}, name={}",
        event.getTradingPartnerId(),
        event.getPreviousStatus(),
        event.getNewStatus(),
        event.getTenantId(),
        event.getPartnerDisplayName());

    auditService.logAction(
        "TRADING_PARTNER_STATUS_CHANGED",
        "trading_partner",
        event.getTradingPartnerId().toString(),
        String.format(
            "Status changed: %s → %s, partner=%s",
            event.getPreviousStatus(), event.getNewStatus(), event.getPartnerDisplayName()));

    if ("BLOCKED".equals(event.getNewStatus())) {
      log.warn(
          "[SECURITY] TradingPartner BLOCKED: id={}, tenant={}, name={}",
          event.getTradingPartnerId(),
          event.getTenantId(),
          event.getPartnerDisplayName());
      // NOTE: NotificationService ile BLOCKED bildirimi ileride eklenecek
    }
  }
}
