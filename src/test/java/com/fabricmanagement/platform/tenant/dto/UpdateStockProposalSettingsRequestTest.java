package com.fabricmanagement.platform.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateStockProposalSettingsRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void theDecidedDefaultsAreValid_andMapToSettings() {
    UpdateStockProposalSettingsRequest request = request(3, 5, 8, "80", "40", "10", "20");

    assertThat(validator.validate(request)).isEmpty();
    assertThat(request.toSettings().minHealthyPiecesSlowing()).isEqualTo(5);
    assertThat(request.toSettings().shortPiecePct()).isEqualByComparingTo("51");
  }

  @Test
  void minimumPiecesMustRiseWithSlowerSales() {
    assertThat(messages(request(5, 3, 8, "80", "40", "10", "20")))
        .contains("Minimum pieces rise with slower sales: moving <= slowing <= stalled");
  }

  @Test
  void theStalledLimitLiesBelowTheSlowingLimit() {
    assertThat(messages(request(3, 5, 8, "40", "40", "10", "20")))
        .contains("The stalled limit lies below the slowing limit");
  }

  @Test
  void aStalledProductMayExceedTheToleranceAtLeastAsFarAsASlowingOne() {
    assertThat(messages(request(3, 5, 8, "80", "40", "20", "10")))
        .contains("A stalled product may exceed the tolerance at least as far as a slowing one");
  }

  @Test
  void everyValueIsRequired_andAToleranceStaysBetweenZeroAndHundred() {
    UpdateStockProposalSettingsRequest missing =
        new UpdateStockProposalSettingsRequest(
            null,
            new BigDecimal("101"),
            3,
            5,
            8,
            new BigDecimal("51"),
            90,
            new BigDecimal("80"),
            new BigDecimal("40"),
            180,
            new BigDecimal("10"),
            new BigDecimal("20"));

    assertThat(validator.validate(missing))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("toleranceDownPct", "toleranceUpPct");
  }

  @Test
  void fabricThresholdAcceptsFourThroughFiftyAndDefaultsOldJson() throws Exception {
    var mapper =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
    var valid = request(3, 5, 8, "80", "40", "10", "20");
    var tree = mapper.valueToTree(valid);
    ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("fabricRollMinHealthyPieces");
    assertThat(
            mapper
                .treeToValue(tree, UpdateStockProposalSettingsRequest.class)
                .fabricRollMinHealthyPieces())
        .isEqualTo(4);
    for (int threshold : new int[] {3, 4, 50, 51}) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) tree)
          .put("fabricRollMinHealthyPieces", threshold);
      var parsed = mapper.treeToValue(tree, UpdateStockProposalSettingsRequest.class);
      var violations =
          validator.validate(parsed).stream().map(v -> v.getPropertyPath().toString()).toList();
      if (threshold == 3 || threshold == 51)
        assertThat(violations).contains("fabricRollMinHealthyPieces");
      else assertThat(violations).isEmpty();
    }
  }

  private static UpdateStockProposalSettingsRequest request(
      int moving,
      int slowing,
      int stalled,
      String slowingBelow,
      String stalledBelow,
      String extraSlowing,
      String extraStalled) {
    return new UpdateStockProposalSettingsRequest(
        new BigDecimal("5"),
        new BigDecimal("5"),
        moving,
        slowing,
        stalled,
        new BigDecimal("51"),
        90,
        new BigDecimal(slowingBelow),
        new BigDecimal(stalledBelow),
        180,
        new BigDecimal(extraSlowing),
        new BigDecimal(extraStalled));
  }

  private static Set<String> messages(UpdateStockProposalSettingsRequest request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }
}
