package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies organization primary address to user as work address.
 *
 * <p>Copy is immutable — changes to organization address do not affect the user's copy. Called when
 * creating a user without explicit addresses so they get a work address from their organization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressAutoService {

  private final OrganizationAddressAssignmentService organizationAddressAssignmentService;
  private final UserAddressAssignmentService userAddressAssignmentService;
  private final AddressService addressService;

  /**
   * Copy organization's primary address to user as work address.
   *
   * <p>Creates a new Address with same fields and assigns it to the user (isPrimary=true,
   * isWorkAddress=true). No-op if organization has no primary address.
   *
   * @param userId User ID
   * @param organizationId Organization ID
   * @param tenantId Tenant ID (for validation)
   */
  @Transactional
  public void copyOrganizationPrimaryAddress(UUID userId, UUID organizationId, UUID tenantId) {
    try {
      Optional<OrganizationAddress> orgAddressOpt =
          organizationAddressAssignmentService.getPrimaryAddress(organizationId);

      if (orgAddressOpt.isEmpty()) {
        log.debug(
            "Organization has no primary address, skipping copy: organizationId={}",
            organizationId);
        return;
      }

      OrganizationAddress orgAddress = orgAddressOpt.get();
      Address sourceAddr =
          addressService
              .findById(orgAddress.getAddressId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Organization address found but Address entity not found: "
                              + orgAddress.getAddressId()));

      Address userWorkAddress =
          addressService.createAddress(
              sourceAddr.getStreetAddress(),
              sourceAddr.getCity(),
              sourceAddr.getState(),
              sourceAddr.getPostalCode(),
              sourceAddr.getCountry(),
              AddressType.OFFICE,
              "Work Address");

      userAddressAssignmentService.assignAddress(userId, userWorkAddress.getId(), true, true);

      log.info(
          "User work address copied from organization: userId={}, organizationId={}",
          userId,
          organizationId);
    } catch (Exception e) {
      log.error(
          "Failed to copy organization primary address: userId={}, organizationId={}. "
              + "User created without default work address. Error: {}",
          userId,
          organizationId,
          e.getMessage());
    }
  }
}
