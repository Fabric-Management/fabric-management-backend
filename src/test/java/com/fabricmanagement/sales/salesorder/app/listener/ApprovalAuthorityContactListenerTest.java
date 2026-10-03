package com.fabricmanagement.sales.salesorder.app.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.event.ContactPointChangedEvent;
import com.fabricmanagement.platform.organization.domain.event.OrganizationContactRemovedEvent;
import com.fabricmanagement.sales.salesorder.app.ApprovalAuthorityService;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A change of a customer's contact point ends the authorities bound to it (ADR-0014 OD-13). */
@ExtendWith(MockitoExtension.class)
class ApprovalAuthorityContactListenerTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID CONTACT = UUID.randomUUID();

  @Mock private ApprovalAuthorityService authorities;

  private ApprovalAuthorityContactListener listener;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    listener = new ApprovalAuthorityContactListener(authorities);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void anAddressChangeEndsTheAuthoritiesForGood() {
    listener.onContactPointChanged(
        new ContactPointChangedEvent(TENANT, CONTACT, ContactPointChangedEvent.ADDRESS_CHANGED));

    verify(authorities)
        .endForContactChange(CONTACT, null, ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);
  }

  @Test
  void aDeletedContactPointEndsThemAsRemoved() {
    listener.onContactPointChanged(
        new ContactPointChangedEvent(TENANT, CONTACT, ContactPointChangedEvent.DELETED));

    verify(authorities).endForContactChange(CONTACT, null, ApprovalAuthorityEnd.CONTACT_REMOVED);
  }

  @Test
  void aContactRemovedFromAnOrganizationEndsThatOrganizationsAuthorities() {
    UUID organization = UUID.randomUUID();

    listener.onOrganizationContactRemoved(
        new OrganizationContactRemovedEvent(TENANT, organization, CONTACT));

    verify(authorities)
        .endForContactChange(CONTACT, organization, ApprovalAuthorityEnd.CONTACT_REMOVED);
  }

  @Test
  void aChangeOutsideItsTenantIsRefusedRatherThanIgnored() {
    ContactPointChangedEvent elsewhere =
        new ContactPointChangedEvent(
            UUID.randomUUID(), CONTACT, ContactPointChangedEvent.ADDRESS_CHANGED);

    assertThatThrownBy(() -> listener.onContactPointChanged(elsewhere))
        .isInstanceOf(IllegalStateException.class);
    verify(authorities, never()).endForContactChange(any(), any(), any());
  }
}
