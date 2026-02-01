package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.company.app.CompanyAddressAssignmentService;
import com.fabricmanagement.common.platform.company.domain.CompanyAddress;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies company primary address to user as work address.
 *
 * <p>Copy is immutable — changes to company address do not affect the user's copy. Called when
 * creating a user without explicit addresses so they get a work address from their company.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressAutoService {

  private final CompanyAddressAssignmentService companyAddressAssignmentService;
  private final UserAddressAssignmentService userAddressAssignmentService;
  private final AddressService addressService;

  /**
   * Copy company's primary address to user as work address.
   *
   * <p>Creates a new Address with same fields and assigns it to the user (isPrimary=true,
   * isWorkAddress=true). No-op if company has no primary address.
   *
   * @param userId User ID
   * @param companyId Company ID
   * @param tenantId Tenant ID (for validation)
   */
  @Transactional
  public void copyCompanyPrimaryAddress(UUID userId, UUID companyId, UUID tenantId) {
    try {
      Optional<CompanyAddress> companyAddressOpt =
          companyAddressAssignmentService.getPrimaryAddress(companyId);

      if (companyAddressOpt.isEmpty()) {
        log.debug("Company has no primary address, skipping copy: companyId={}", companyId);
        return;
      }

      CompanyAddress companyAddress = companyAddressOpt.get();
      Address companyAddr =
          addressService
              .findById(companyAddress.getAddressId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Company address found but Address entity not found: "
                              + companyAddress.getAddressId()));

      Address userWorkAddress =
          addressService.createAddress(
              companyAddr.getStreetAddress(),
              companyAddr.getCity(),
              companyAddr.getState(),
              companyAddr.getPostalCode(),
              companyAddr.getCountry(),
              AddressType.OFFICE,
              "Work Address");

      userAddressAssignmentService.assignAddress(userId, userWorkAddress.getId(), true, true);

      log.info("User work address copied from company: userId={}, companyId={}", userId, companyId);
    } catch (Exception e) {
      log.warn(
          "Failed to copy company primary address: userId={}, companyId={}, error={}",
          userId,
          companyId,
          e.getMessage());
    }
  }
}
