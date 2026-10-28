package com.fabricmanagement.platform.user.dto;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class OnboardingStepRequestTest {
  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Test
  void typedPayloadRejectsCrossStepFieldsEvenWhenGlobalUnknownFieldChecksAreDisabled() {
    assertThatThrownBy(
            () ->
                json.readValue(
                    """
        {"expectedVersion":0,"operationId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
         "payload":{"stepId":"YOUR_DETAILS","mobilePhone":"+447700900123","companyEmail":"office@example.com"}}
        """,
                    SaveOnboardingStepRequest.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertThatThrownBy(
            () ->
                json.readValue(
                    """
        {"expectedVersion":0,"operationId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","tenantId":"untrusted",
         "payload":{"stepId":"YOUR_DETAILS","mobilePhone":"+447700900123"}}
        """,
                    SaveOnboardingStepRequest.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }

  @Test
  void modulesRequireCatalogueVersionAndRejectClientPricingFields() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(
              factory
                  .getValidator()
                  .validate(
                      new OnboardingStepPayload.PlatformModules(java.util.List.of("FabricOS"))))
          .isNotEmpty();
      assertThat(
              factory
                  .getValidator()
                  .validate(
                      new OnboardingStepPayload.PlatformModules(
                          java.util.List.of("FabricOS"), "catalogue-version")))
          .isEmpty();
    }
    assertThatThrownBy(
            () ->
                json.readValue(
                    """
        {"stepId":"PLATFORM_MODULES","selectedOS":["FabricOS"],
         "catalogueVersion":"catalogue-version","pricingTier":"Enterprise"}
        """,
                    OnboardingStepPayload.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }

  @Test
  void unknownStepAndUnconfirmedReviewCannotBecomeAcceptedEvidence() throws Exception {
    assertThatThrownBy(
            () -> json.readValue("{\"stepId\":\"CHECKOUT\"}", OnboardingStepPayload.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(new OnboardingStepPayload.Review(false, 5L)))
          .isNotEmpty();
      assertThat(
              factory
                  .getValidator()
                  .validate(
                      new OnboardingStepPayload.PlatformModules(java.util.List.of("WarehouseOS"))))
          .isNotEmpty();
    }
  }
}
