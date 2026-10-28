package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.dto.OnboardingStepPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Explicit typed allowlist; neither signup credentials nor demo-company lookups enter evidence. */
@Component
@RequiredArgsConstructor
public class OnboardingEvidenceMapper {
  private final ObjectMapper json;
  private final ContactService contacts;

  public CompleteOnboardingRequest patch(OnboardingStepPayload payload) {
    return switch (payload) {
      case OnboardingStepPayload.OrganizationDetails o ->
          CompleteOnboardingRequest.builder()
              .organizationName(o.organizationName())
              .taxId(o.taxId())
              .organizationType(o.organizationType())
              .legalName(o.legalName())
              .registrationNumber(o.registrationNumber())
              .industry(o.industry())
              .website(o.website())
              .description(o.description())
              .build();
      case OnboardingStepPayload.Address a ->
          CompleteOnboardingRequest.builder()
              .addressLine1(a.addressLine1())
              .addressLine2(a.addressLine2())
              .city(a.city())
              .state(a.state())
              .district(a.district())
              .postalCode(a.postalCode())
              .country(a.country())
              .addressLabel(a.addressLabel())
              .build();
      case OnboardingStepPayload.YourDetails p ->
          CompleteOnboardingRequest.builder().mobilePhone(p.mobilePhone()).build();
      case OnboardingStepPayload.OrganizationContacts c ->
          CompleteOnboardingRequest.builder()
              .companyEmail(c.companyEmail())
              .companyPhone(c.companyPhone())
              .build();
      case OnboardingStepPayload.PlatformModules m ->
          CompleteOnboardingRequest.builder().selectedOS(m.selectedOS()).build();
      case OnboardingStepPayload.Review ignored -> CompleteOnboardingRequest.builder().build();
    };
  }

  public CompanyEvidence merge(CompanyEvidence previous, CompleteOnboardingRequest patch) {
    ObjectNode merged = json.valueToTree(flatten(previous));
    ObjectNode supplied = json.valueToTree(patch);
    var known = new HashSet<>(previous.knownFields());
    supplied
        .fields()
        .forEachRemaining(
            field -> {
              if (!field.getValue().isNull()) {
                known.add(field.getKey());
                var value = field.getValue();
                if (value.isTextual()) {
                  String trimmed = value.textValue().trim();
                  value =
                      trimmed.isEmpty()
                          ? json.getNodeFactory().nullNode()
                          : json.getNodeFactory().textNode(trimmed);
                }
                merged.set(field.getKey(), value);
              }
            });
    CompleteOnboardingRequest data = json.convertValue(merged, CompleteOnboardingRequest.class);
    if (patch.getCompanyEmail() != null)
      data.setCompanyEmail(
          contacts.prepareOnboardingContact(patch.getCompanyEmail(), ContactType.EMAIL).value());
    if (patch.getCompanyPhone() != null)
      data.setCompanyPhone(
          contacts.prepareOnboardingContact(patch.getCompanyPhone(), null).value());
    var original = previous.registrant();
    boolean hasAddress =
        previous.headquarters() != null
            || java.util.stream.Stream.of(
                    patch.getAddressLine1(),
                    patch.getAddressLine2(),
                    patch.getCity(),
                    patch.getState(),
                    patch.getDistrict(),
                    patch.getPostalCode(),
                    patch.getCountry(),
                    patch.getAddressLabel())
                .anyMatch(java.util.Objects::nonNull);
    boolean hasContacts =
        previous.contacts() != null
            || patch.getCompanyEmail() != null
            || patch.getCompanyPhone() != null;
    return new CompanyEvidence(
        new CompanyEvidence.Organisation(
            data.getOrganizationName(),
            data.getTaxId(),
            data.getOrganizationType(),
            data.getLegalName(),
            data.getRegistrationNumber(),
            data.getIndustry(),
            data.getWebsite(),
            data.getDescription()),
        hasAddress
            ? new CompanyEvidence.Headquarters(
                data.getAddressLine1(),
                data.getAddressLine2(),
                data.getCity(),
                data.getState(),
                data.getDistrict(),
                data.getPostalCode(),
                data.getCountry(),
                data.getAddressLabel())
            : null,
        new CompanyEvidence.Registrant(
            original.firstName(), original.lastName(), original.workEmail(), data.getMobilePhone()),
        hasContacts
            ? new CompanyEvidence.CompanyContacts(data.getCompanyEmail(), data.getCompanyPhone())
            : null,
        data.getSelectedOS(),
        known);
  }

  private CompleteOnboardingRequest flatten(CompanyEvidence evidence) {
    var b = CompleteOnboardingRequest.builder();
    if (evidence.organisation() != null) {
      var o = evidence.organisation();
      b.organizationName(o.organizationName())
          .taxId(o.taxId())
          .organizationType(o.organizationType())
          .legalName(o.legalName())
          .registrationNumber(o.registrationNumber())
          .industry(o.industry())
          .website(o.website())
          .description(o.description());
    }
    if (evidence.headquarters() != null) {
      var a = evidence.headquarters();
      b.addressLine1(a.addressLine1())
          .addressLine2(a.addressLine2())
          .city(a.city())
          .state(a.state())
          .district(a.district())
          .postalCode(a.postalCode())
          .country(a.country())
          .addressLabel(a.addressLabel());
    }
    if (evidence.contacts() != null)
      b.companyEmail(evidence.contacts().companyEmail())
          .companyPhone(evidence.contacts().companyPhone());
    if (evidence.registrant() != null) b.mobilePhone(evidence.registrant().mobilePhone());
    return b.selectedOS(evidence.selectedOS()).build();
  }
}
