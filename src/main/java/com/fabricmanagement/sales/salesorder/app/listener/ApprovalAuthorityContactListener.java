package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.event.ContactPointChangedEvent;
import com.fabricmanagement.platform.organization.domain.event.OrganizationContactRemovedEvent;
import com.fabricmanagement.sales.salesorder.app.ApprovalAuthorityService;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ends the approval authorities bound to a customer's contact point when the card changes it
 * (ADR-0014 OD-13). Deliberately a synchronous listener, not an after-commit one: the authority
 * ends in the very transaction that changed the contact point, so there is no moment after the
 * change in which a link, a code or a decision could still use the earlier address — and a change
 * back to that address later finds the authority already ended.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalAuthorityContactListener {

  private final ApprovalAuthorityService authorities;

  @EventListener
  public void onContactPointChanged(ContactPointChangedEvent event) {
    end(
        event.getTenantId(),
        event.getContactId(),
        null,
        ContactPointChangedEvent.DELETED.equals(event.getChange())
            ? ApprovalAuthorityEnd.CONTACT_REMOVED
            : ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);
  }

  @EventListener
  public void onOrganizationContactRemoved(OrganizationContactRemovedEvent event) {
    end(
        event.getTenantId(),
        event.getContactId(),
        event.getOrganizationId(),
        ApprovalAuthorityEnd.CONTACT_REMOVED);
  }

  private void end(UUID tenantId, UUID contactId, UUID organizationId, ApprovalAuthorityEnd cause) {
    UUID current = TenantContext.getCurrentTenantIdOrNull();
    if (current == null || !current.equals(tenantId)) {
      // The change was made in its own tenant; anything else is a programming error.
      throw new IllegalStateException("A contact change arrived outside its tenant");
    }
    int ended = authorities.endForContactChange(contactId, organizationId, cause);
    if (ended > 0) {
      log.info("{} approval authorities ended: contact {} {}", ended, contactId, cause);
    }
  }
}
