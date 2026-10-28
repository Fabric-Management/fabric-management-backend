package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.app.ContactService.OnboardingContact;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.app.OnboardingRequestValidator;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One transaction-participating profile writer for completion and future authorised step saves. */
@Service
@RequiredArgsConstructor
public class CompanyOnboardingProfileService {
  private final OrganizationRepository organizations;
  private final OrganizationAddressAssignmentService addresses;
  private final OrganizationContactAssignmentService contacts;
  private final AddressService addressService;
  private final ContactService contactService;
  private final OnboardingRequestValidator validator;

  /**
   * Opaque preflight result: all validation and locks precede any writes, including personal data.
   */
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class PreparedProfile {
    private final Organization root;
    private final CompleteOnboardingRequest request;
    private final OrganizationAddress headquarters;
    private final CreateAddressRequest address;
    private final OnboardingContact email;
    private final OnboardingContact phone;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public PreparedProfile prepare(CompleteOnboardingRequest request) {
    validator.validate(request);
    UUID tenant = TenantContext.requireTenantId();
    Organization root =
        organizations
            .lockRootForOnboarding(tenant)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Root organisation not found",
                        "ONBOARDING_ROOT_ORGANIZATION_NOT_FOUND",
                        409));
    if (request.getOrganizationType() == OrganizationType.EXTERNAL_PARTNER) {
      throw new PlatformDomainException(
          "Root organisation cannot be an external partner", "ONBOARDING_PROFILE_INVALID", 422);
    }
    String taxId = patch(request.getTaxId(), root.getTaxId());
    if (!Objects.equals(root.getTaxId(), taxId)
        && organizations.existsByTenantIdAndTaxId(tenant, taxId)) {
      throw new PlatformDomainException(
          "Organisation tax ID already exists", "TAX_ID_ALREADY_EXISTS", 409);
    }
    OrganizationAddress headquarters = addresses.getHeadquarters(root.getId()).orElse(null);
    if (headquarters == null) {
      // Historical sales-led setup assigned a HEADQUARTERS card as primary but omitted the HQ flag.
      headquarters =
          addresses
              .getPrimaryAddress(root.getId())
              .filter(
                  assignment ->
                      addressService
                          .findById(assignment.getAddressId())
                          .map(card -> card.getAddressType() == AddressType.HEADQUARTERS)
                          .orElse(false))
              .orElse(null);
    }
    CreateAddressRequest address = null;
    if (hasAddressPatch(request)) {
      Address current =
          headquarters == null
              ? null
              : addressService
                  .findById(headquarters.getAddressId())
                  .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                  .orElseThrow(
                      () ->
                          new PlatformDomainException(
                              "Headquarters address not found",
                              "ONBOARDING_HQ_ADDRESS_NOT_FOUND",
                              404));
      address = mergeAddress(request, current);
      if (current != null) {
        if (sameAddress(address, current)) {
          address = null;
        } else {
          addressService.requireExclusiveOnboardingAddress(current.getId(), root.getId());
        }
      }
    }
    return new PreparedProfile(
        root,
        request,
        headquarters,
        address,
        contactService.prepareOnboardingContact(request.getCompanyEmail(), ContactType.EMAIL),
        contactService.prepareOnboardingContact(request.getCompanyPhone(), null));
  }

  /** Caller must authorise company setup first; no independent transaction is opened. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void save(PreparedProfile prepared) {
    Organization root = prepared.root;
    var request = prepared.request;
    root.setName(patch(request.getOrganizationName(), root.getName()));
    if (request.getOrganizationType() != null)
      root.setOrganizationType(request.getOrganizationType());
    root.setTaxId(patch(request.getTaxId(), root.getTaxId()));
    root.setLegalName(patch(request.getLegalName(), root.getLegalName()));
    root.setRegistrationNumber(
        patch(request.getRegistrationNumber(), root.getRegistrationNumber()));
    root.setIndustry(patch(request.getIndustry(), root.getIndustry()));
    root.setWebsite(patch(request.getWebsite(), root.getWebsite()));
    root.setDescription(patch(request.getDescription(), root.getDescription()));
    organizations.save(root);
    if (prepared.address != null) {
      if (prepared.headquarters == null) {
        Address created = addressService.createAddress(prepared.address);
        addresses.assignAddress(root.getId(), created.getId(), true, true);
      } else {
        if (!Boolean.TRUE.equals(prepared.headquarters.getIsHeadquarters())) {
          addresses.setAsHeadquarters(root.getId(), prepared.headquarters.getAddressId());
        }
        addressService.replaceOnboardingHeadquarters(
            prepared.headquarters.getAddressId(), root.getId(), prepared.address);
      }
    }
    assign(root.getId(), prepared.email, true);
    assign(root.getId(), prepared.phone, false);
  }

  private void assign(UUID organizationId, OnboardingContact prepared, boolean isDefault) {
    if (prepared == null) return;
    var contact = contactService.reuseOrCreateOnboardingContact(prepared);
    contacts.ensureOnboardingContact(organizationId, contact.getId(), isDefault);
  }

  private boolean hasAddressPatch(CompleteOnboardingRequest request) {
    return Stream.of(
            request.getAddressLine1(),
            request.getAddressLine2(),
            request.getCity(),
            request.getState(),
            request.getDistrict(),
            request.getPostalCode(),
            request.getCountry(),
            request.getAddressLabel())
        .anyMatch(Objects::nonNull);
  }

  private boolean sameAddress(CreateAddressRequest merged, Address current) {
    return Objects.equals(merged.getStreetAddress(), current.getStreetAddress())
        && Objects.equals(merged.getCity(), current.getCity())
        && Objects.equals(merged.getCountry(), current.getCountry())
        && Objects.equals(merged.getAddressLine2(), current.getAddressLine2())
        && Objects.equals(merged.getState(), current.getState())
        && Objects.equals(merged.getDistrict(), current.getDistrict())
        && Objects.equals(merged.getPostalCode(), current.getPostalCode())
        && Objects.equals(merged.getLabel(), current.getLabel());
  }

  private CreateAddressRequest mergeAddress(CompleteOnboardingRequest request, Address current) {
    String street =
        patch(request.getAddressLine1(), current == null ? null : current.getStreetAddress());
    String city = patch(request.getCity(), current == null ? null : current.getCity());
    String country = patch(request.getCountry(), current == null ? null : current.getCountry());
    if (street == null || city == null || country == null) {
      throw new PlatformDomainException(
          "New headquarters requires street, city and country",
          "ONBOARDING_HQ_ADDRESS_INCOMPLETE",
          422);
    }
    return CreateAddressRequest.builder()
        .streetAddress(street)
        .city(city)
        .country(country)
        .addressLine2(
            patch(request.getAddressLine2(), current == null ? null : current.getAddressLine2()))
        .state(patch(request.getState(), current == null ? null : current.getState()))
        .district(patch(request.getDistrict(), current == null ? null : current.getDistrict()))
        .postalCode(
            patch(request.getPostalCode(), current == null ? null : current.getPostalCode()))
        .label(
            patch(request.getAddressLabel(), current == null ? "Headquarters" : current.getLabel()))
        .addressType(AddressType.HEADQUARTERS)
        .build();
  }

  /** Null is no change; an explicitly supplied blank string clears an optional scalar. */
  private String patch(String supplied, String existing) {
    return supplied == null ? existing : supplied.isBlank() ? null : supplied.trim();
  }
}
