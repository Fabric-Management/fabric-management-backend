package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.common.platform.organization.app.OrganizationContactAssignmentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 4: Assign contact and address to organization (sales-led only).
 *
 * <p>Creates address/contact via Communication module, then assigns to organization using
 * CompanyAddressAssignmentService and CompanyContactAssignmentService (common_organization table).
 */
@Order(5) // After CreateAdminUserStep (4)
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignContactAndAddressStep implements OnboardingStep {

  private final AddressService addressService;
  private final ContactService contactService;
  private final OrganizationAddressAssignmentService addressAssignmentService;
  private final OrganizationContactAssignmentService contactAssignmentService;

  @Override
  public void execute(OnboardingContext context) {
    if (!context.isSalesLed()) {
      return;
    }
    UUID organizationId = context.getOrganizationId();
    UUID tenantId = context.getTenantId();
    if (organizationId == null || tenantId == null) {
      return;
    }

    // Create and assign address if provided
    if (context.getAddress() != null || context.getCity() != null || context.getCountry() != null) {
      var address =
          addressService.createAddress(
              context.getAddress() != null ? context.getAddress() : "",
              context.getCity() != null ? context.getCity() : "",
              context.getState(),
              context.getDistrict(),
              context.getPostalCode(),
              context.getCountry() != null ? context.getCountry() : "",
              null, // countryCode
              AddressType.HEADQUARTERS,
              "Organization");
      addressAssignmentService.assignAddress(organizationId, address.getId(), true, false);
    }

    // Create and assign contact if provided (email or phone)
    if (context.getCompanyEmail() != null && !context.getCompanyEmail().isBlank()) {
      var contact =
          contactService.createContact(
              context.getCompanyEmail(), ContactType.EMAIL, "Organization", false, null);
      contactAssignmentService.assignContact(organizationId, contact.getId(), true, null);
    }
    if (context.getPhoneNumber() != null && !context.getPhoneNumber().isBlank()) {
      var contact =
          contactService.createContact(
              context.getPhoneNumber(), ContactType.MOBILE, "Organization", false, null);
      contactAssignmentService.assignContact(organizationId, contact.getId(), false, null);
    }

    log.debug("AssignContactAndAddressStep: organizationId={}", organizationId);
  }
}
