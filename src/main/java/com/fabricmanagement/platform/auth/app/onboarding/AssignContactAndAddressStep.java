package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
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
      CreateAddressRequest createRequest =
          CreateAddressRequest.builder()
              .streetAddress(context.getAddress() != null ? context.getAddress() : "")
              .city(context.getCity() != null ? context.getCity() : "")
              .state(context.getState())
              .district(context.getDistrict())
              .postalCode(context.getPostalCode())
              .country(context.getCountry() != null ? context.getCountry() : "")
              .addressType(AddressType.HEADQUARTERS)
              .label("Organization")
              .build();
      var address = addressService.createAddress(createRequest);
      addressAssignmentService.assignAddress(organizationId, address.getId(), true, false);
    }

    // Create and assign contact if provided (email or phone)
    if (context.getOrganizationEmail() != null && !context.getOrganizationEmail().isBlank()) {
      var contact =
          contactService.createContact(
              context.getOrganizationEmail(), ContactType.EMAIL, "Organization", false, null);
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
