package com.fabricmanagement.platform.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteOnboardingRequestTest {

  @Test
  void onlyPersonalMobileIsSelfService() {
    assertThat(CompleteOnboardingRequest.builder().build().hasCompanyChanges()).isFalse();
    assertThat(
            CompleteOnboardingRequest.builder()
                .mobilePhone("+447700900123")
                .build()
                .hasCompanyChanges())
        .isFalse();
  }

  @Test
  void everyOtherRequestFieldIncludingEmptyValuesRequiresCompanyAuthority() throws Exception {
    // Fail closed when another DTO field is added: it must be classified or explicitly personal.
    for (Field field : CompleteOnboardingRequest.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          || field.isSynthetic()
          || field.getName().equals("mobilePhone")) continue;
      field.setAccessible(true);
      CompleteOnboardingRequest request = new CompleteOnboardingRequest();
      Object value;
      if (field.getType() == String.class) value = "";
      else if (field.getType() == OrganizationType.class) value = OrganizationType.WEAVER;
      else if (field.getType() == List.class) value = List.of();
      else throw new AssertionError("Classify new onboarding field: " + field.getName());
      field.set(request, value);
      assertThat(request.hasCompanyChanges()).as(field.getName()).isTrue();
      if (field.getType() == String.class) {
        field.set(request, "   ");
        assertThat(request.hasCompanyChanges()).as("blank " + field.getName()).isTrue();
      }
    }
  }

  @Test
  void classifierIsNotPartOfTheWirePayload() throws Exception {
    String json = new ObjectMapper().writeValueAsString(new CompleteOnboardingRequest());
    assertThat(json).doesNotContain("companyChanges", "hasCompanyChanges");
  }
}
