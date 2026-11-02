package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class AssignContactAndAddressStepTest {
  private final AddressService addresses = mock(AddressService.class);
  private final ContactService contacts = mock(ContactService.class);
  private final OrganizationAddressAssignmentService addressAssignments =
      mock(OrganizationAddressAssignmentService.class);
  private final OrganizationContactAssignmentService contactAssignments =
      mock(OrganizationContactAssignmentService.class);
  private final SignupEvidencePort evidence = mock(SignupEvidencePort.class);
  private final OnboardingJourneyProvisioningPort journeys =
      mock(OnboardingJourneyProvisioningPort.class);
  private final AssignContactAndAddressStep step =
      new AssignContactAndAddressStep(addresses, contacts, addressAssignments, contactAssignments);

  static Stream<Arguments> partialAddresses() {
    return Stream.of(
        Arguments.of(null, "Leeds", "United Kingdom"),
        Arguments.of("", "Leeds", "United Kingdom"),
        Arguments.of("  ", "Leeds", "United Kingdom"),
        Arguments.of("14 Mill Lane", null, "United Kingdom"),
        Arguments.of("14 Mill Lane", "  ", "United Kingdom"),
        Arguments.of("14 Mill Lane", "Leeds", null),
        Arguments.of("14 Mill Lane", "Leeds", "  "),
        Arguments.of(null, null, null));
  }

  @ParameterizedTest
  @MethodSource("partialAddresses")
  void partialSignupContinuesAndRetainsRawEvidenceWithoutCreatingAPostalCard(
      String street, String city, String country) {
    OnboardingContext context = context();
    context.setAddress(street);
    context.setCity(city);
    context.setCountry(country);
    context.setDistrict("Holbeck");
    context.setPostalCode("LS1 1AA");

    // Exercise the consumer and the downstream evidence step in the actual orchestrator.
    SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    try {
      var orchestrator =
          new TenantOnboardingOrchestrator(
              List.of(step, new CaptureSignupEvidenceStep(evidence, journeys)), metrics);
      assertThat(orchestrator.onboard(context).getOrganizationId())
          .isEqualTo(context.getOrganizationId());
    } finally {
      metrics.close();
    }

    verifyNoInteractions(addresses, addressAssignments);
    var captured = ArgumentCaptor.forClass(SignupCompany.class);
    verify(evidence).capture(captured.capture());
    var facts = captured.getValue().facts();
    assertThat(facts.headquarters().addressLine1()).isEqualTo(street);
    assertThat(facts.headquarters().city()).isEqualTo(city);
    assertThat(facts.headquarters().country()).isEqualTo(country);
    assertThat(facts.headquarters().district()).isEqualTo("Holbeck");
    assertThat(facts.headquarters().postalCode()).isEqualTo("LS1 1AA");
    assertThat(facts.knownFields().contains("addressLine1")).isEqualTo(street != null);
    assertThat(facts.knownFields().contains("city")).isEqualTo(city != null);
    assertThat(facts.knownFields().contains("country")).isEqualTo(country != null);
    verify(journeys)
        .provision(context.getTenantId(), context.getUserId(), context.getSignupId(), "SALES_LED");
  }

  @Test
  void completeAddressStillCreatesAndAssignsHeadquarters() {
    OnboardingContext context = context();
    context.setAddress("14 Mill Lane");
    context.setCity("Leeds");
    context.setCountry("United Kingdom");
    context.setState("West Yorkshire");
    context.setDistrict("Holbeck");
    context.setPostalCode("LS1 1AA");
    Address address = Address.builder().build();
    address.setId(UUID.randomUUID());
    when(addresses.createAddress(any(CreateAddressRequest.class))).thenReturn(address);

    step.execute(context);

    var request = ArgumentCaptor.forClass(CreateAddressRequest.class);
    verify(addresses).createAddress(request.capture());
    assertThat(request.getValue().getStreetAddress()).isEqualTo("14 Mill Lane");
    assertThat(request.getValue().getCity()).isEqualTo("Leeds");
    assertThat(request.getValue().getCountry()).isEqualTo("United Kingdom");
    assertThat(request.getValue().getState()).isEqualTo("West Yorkshire");
    assertThat(request.getValue().getDistrict()).isEqualTo("Holbeck");
    assertThat(request.getValue().getPostalCode()).isEqualTo("LS1 1AA");
    assertThat(request.getValue().getAddressType()).isEqualTo(AddressType.HEADQUARTERS);
    assertThat(request.getValue().getLabel()).isNotBlank();
    verify(addressAssignments)
        .assignAddress(context.getOrganizationId(), address.getId(), true, true);
  }

  @Test
  void missingStreetDoesNotSkipCompanyContactAssignment() {
    OnboardingContext context = context();
    context.setCity("Leeds");
    context.setCountry("United Kingdom");
    context.setOrganizationEmail("office@hawthorn.example");
    Contact contact = mock(Contact.class);
    UUID contactId = UUID.randomUUID();
    when(contact.getId()).thenReturn(contactId);
    when(contacts.createContact(
            "office@hawthorn.example", ContactType.EMAIL, "Organization", false, null))
        .thenReturn(contact);

    step.execute(context);

    verifyNoInteractions(addresses, addressAssignments);
    verify(contactAssignments).assignContact(context.getOrganizationId(), contactId, true, null);
  }

  private OnboardingContext context() {
    OnboardingContext context = new OnboardingContext();
    context.setSalesLed(true);
    context.setTenantId(UUID.randomUUID());
    context.setOrganizationId(UUID.randomUUID());
    context.setUserId(UUID.randomUUID());
    context.setOrganizationName("Hawthorn Weaving Ltd");
    context.setOrganizationType(OrganizationType.WEAVER);
    context.setAdminFirstName("Eleanor");
    context.setAdminLastName("Hughes");
    context.setAdminContact("eleanor.hughes@example.com");
    context.setSignupIntent("SALES_LED");
    return context;
  }
}
