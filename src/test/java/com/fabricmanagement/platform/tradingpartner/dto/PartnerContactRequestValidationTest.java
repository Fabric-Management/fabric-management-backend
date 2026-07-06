package com.fabricmanagement.platform.tradingpartner.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartnerContactRequestValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void createPartnerContactRequestRejectsInvalidFields() {
    CreatePartnerContactRequest request = new CreatePartnerContactRequest();
    request.setName("");
    request.setEmail("not-an-email");
    request.setPhone("x".repeat(31));

    assertThat(violatedFields(request)).contains("name", "email", "phone", "role");
  }

  @Test
  void quickCreateCustomerRequiresCompanyNameAndAtLeastOneValidContact() {
    QuickCreateCustomerContactRequest contact = new QuickCreateCustomerContactRequest();
    contact.setName("");
    contact.setEmail("bad-email");

    QuickCreateCustomerRequest request = new QuickCreateCustomerRequest();
    request.setCompanyName("");
    request.setTaxNumber("x".repeat(51));
    request.setAddress("x".repeat(501));
    request.setPhone("x".repeat(31));
    request.setContacts(List.of(contact));

    assertThat(violatedFields(request))
        .contains(
            "companyName",
            "taxNumber",
            "address",
            "phone",
            "contacts[0].name",
            "contacts[0].email");
  }

  @Test
  void quickCreateCustomerContactDefaultsToBuyer() {
    QuickCreateCustomerContactRequest contact = new QuickCreateCustomerContactRequest();

    assertThat(contact.getRole()).isEqualTo(PartnerContactRole.BUYER);
    assertThat(contact.getWhatsappEnabled()).isFalse();
  }

  private static Set<String> violatedFields(Object request) {
    return validator.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
