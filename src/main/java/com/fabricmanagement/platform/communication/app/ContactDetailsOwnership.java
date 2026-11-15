package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationContactRepository;
import com.fabricmanagement.platform.organization.security.OrganizationWriteAccess;
import com.fabricmanagement.platform.user.domain.UserAddress;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.infra.repository.UserAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.platform.user.security.UserContactDetailsAccess;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks who may reuse an existing contact or address in a different owner card. */
@Component
@RequiredArgsConstructor
public class ContactDetailsOwnership {

  private final UserContactRepository userContacts;
  private final UserAddressRepository userAddresses;
  private final OrganizationContactRepository organisationContacts;
  private final OrganizationAddressRepository organisationAddresses;
  private final UserContactDetailsAccess userAccess;
  private final OrganizationWriteAccess organisationAccess;

  public void requireContactReuse(UUID contactId, boolean targetIsUser) {
    UUID tenant = TenantContext.requireTenantId();
    var organisations =
        organisationContacts.findByTenantIdAndContactIdAndIsActiveTrue(tenant, contactId);
    if (targetIsUser) {
      organisations.forEach(
          link -> organisationAccess.requireContactDetails(link.getOrganizationId()));
    }
    if (organisations.isEmpty()) {
      requireUserSource(
          userContacts.findByTenantIdAndContactId(tenant, contactId).stream()
              .filter(link -> Boolean.TRUE.equals(link.getIsActive()))
              .map(UserContact::getUserId)
              .toList());
    }
  }

  public void requireExtensionParent(UUID parentId, UUID ownerId, boolean ownerIsUser) {
    if (parentId == null) return;
    UUID tenant = TenantContext.requireTenantId();
    boolean linked =
        ownerIsUser
            ? userContacts.findByTenantIdAndContactId(tenant, parentId).stream()
                .anyMatch(
                    link ->
                        ownerId.equals(link.getUserId()) && Boolean.TRUE.equals(link.getIsActive()))
            : organisationContacts
                .findByTenantIdAndContactIdAndIsActiveTrue(tenant, parentId)
                .stream()
                .anyMatch(link -> ownerId.equals(link.getOrganizationId()));
    if (!linked) {
      throw new PlatformDomainException(
          "The extension must use a landline on this owner's card",
          "CONTACT_EXTENSION_PARENT_REQUIRED",
          403);
    }
  }

  public void requireAddressReuse(UUID addressId, boolean targetIsUser) {
    UUID tenant = TenantContext.requireTenantId();
    var org =
        organisationAddresses
            .findByAddressId(addressId)
            .filter(link -> tenant.equals(link.getTenantId()));
    if (targetIsUser) {
      org.ifPresent(link -> organisationAccess.requireContactDetails(link.getOrganizationId()));
    }
    if (org.isEmpty()) {
      requireUserSource(
          userAddresses.findByTenantIdAndAddressIdAndIsActiveTrue(tenant, addressId).stream()
              .map(UserAddress::getUserId)
              .toList());
    }
  }

  private void requireUserSource(List<UUID> owners) {
    if (!owners.isEmpty() && owners.stream().noneMatch(userAccess::canWrite)) {
      throw new PlatformDomainException(
          "This record belongs to another user", "CONTACT_BELONGS_TO_ANOTHER_USER", 403);
    }
  }
}
