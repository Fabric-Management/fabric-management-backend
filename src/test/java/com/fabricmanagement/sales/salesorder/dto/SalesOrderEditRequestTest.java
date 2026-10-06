package com.fabricmanagement.sales.salesorder.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.FieldInstruction;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.Parsed;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSamples;
import com.fabricmanagement.sales.salesorder.domain.requirement.UnmodelledSpecConstraint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The safe-edit request as the server reads it (CEDIT-02 §4.2/§4.3, §15): Jackson strictness and
 * presence at every typed level (400), Bean Validation, and the contract rules of {@link
 * SalesOrderEditInstructions#parse} (422 with code and place). The mapper mirrors the application's
 * global one, which ignores unknown properties: strictness must come from the DTOs themselves.
 *
 * <p>Bodies are written with single quotes for readability and placeholders for fixed ids: {@code
 * $op}, {@code $base}, {@code $l1}, {@code $l2}, {@code $l3}, {@code $c1}, {@code $c2}, {@code
 * $p3}, {@code $actor}.
 */
class SalesOrderEditRequestTest {

  private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-00000000000f");
  private static final UUID OTHER_ORDER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
  private static final UUID OPERATION = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID BASE = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID L1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID L2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID L3 = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
  private static final UUID C1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID C2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
  private static final UUID P3 = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
  private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final int MAX_LINE_OPERATIONS = 200;

  private static final String NOTES_SET = "'notes':{'operation':'SET','value':'Urgent'}";

  private static final String PROFILE =
      "{'basis':{'kind':'LINE_EXPLICIT','actorId':'$actor','decidedAt':'2026-09-18T10:00:00Z',"
          + "'decisionReference':'line-explicit'},'scopeVersion':'fabric-v1',"
          + "'resolutionRuleVersion':'sales-req-v1'}";

  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("S2.7: a CLEAR built in Java is written without a value and reads back as CLEAR")
  void clearIsWrittenWithoutAValue() throws Exception {
    String written = mapper.writeValueAsString(SalesOrderTextFieldEdit.clear());

    assertThat(mapper.readTree(written).has("value")).isFalse();
    SalesOrderTextFieldEdit read = mapper.readValue(written, SalesOrderTextFieldEdit.class);
    assertThat(read.getOperation()).isEqualTo(SalesOrderFieldEditOperation.CLEAR);
    assertThat(read.valueGiven()).isFalse();
  }

  // ── 400: unknown properties at every typed level ───────────────────────────

  static Stream<Arguments> unknownProperties() {
    return Stream.of(
        Arguments.of(
            "S15.1: unknown root property 'force'",
            "{'operationId':'$op','baseId':'$base','force':true,'header':{" + NOTES_SET + "}}"),
        Arguments.of(
            "S11.2: the client cannot send a base value",
            "{'operationId':'$op','baseId':'$base','base':{'notes':null},'header':{"
                + NOTES_SET
                + "}}"),
        Arguments.of(
            "S15.2, S2.9: unknown header key",
            body(
                "'header':{"
                    + NOTES_SET
                    + ",'priority':{'operation':'SET','value':'HIGH'},"
                    + "'discountNote':{'operation':'SET','value':'x'}}")),
        Arguments.of(
            "S15.3: unknown property of a field instruction, even with a null value",
            body("'header':{'notes':{'operation':'SET','value':'Urgent','previous':null}}")),
        Arguments.of(
            "S15.4: unknown property of a composite value",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'pricing':{'operation':'SET','value':{'currency':'GBP','unitPrice':4,"
                    + "'vat':1}}}}]")),
        Arguments.of(
            "S15.5: unknown property of a line operation",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','position':2,'fields':{"
                    + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]")),
        Arguments.of(
            "S15.6: unknown key in a line's field container",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'colour':{'operation':'SET','value':'$c1'}}}]")),
        Arguments.of(
            "S6.9: a product id inside an UPDATE's field container",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'productId':{'operation':'SET','value':'$p3'}}}]")),
        Arguments.of(
            "S15.7: unknown property of a resolution",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'resolutions':[{'key':'paymentTerms','choice':'USE_MINE',"
                    + "'force':true}]")),
        Arguments.of(
            "S15.8: unknown property of the requirement profile's basis",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'FABRIC',"
                    + "'requirementProfile':"
                    + PROFILE.replace("'line-explicit'}", "'line-explicit','extra':1}")
                    + "}}}}]")),
        Arguments.of(
            "S15.8: unknown property of the requirement profile itself",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'FABRIC',"
                    + "'requirementProfile':"
                    + PROFILE.replace("'sales-req-v1'}", "'sales-req-v1','extra':1}")
                    + "}}}}]")),
        Arguments.of(
            "S15.8: unknown property of the specification value",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'FABRIC',"
                    + "'profileFingerprint':'F1'}}}}]")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unknownProperties")
  @DisplayName("S15.1-S15.8, S2.9, S6.9, S11.2: an unknown property at any level is a 400")
  void unknownPropertyIsUnreadable(String scenario, String body) {
    assertUnreadable(body);
  }

  static Stream<Arguments> unknownEnums() {
    return Stream.of(
        Arguments.of(
            "S15.9, S2.9: unknown field operation MERGE",
            body("'header':{'notes':{'operation':'MERGE','value':'Urgent'}}")),
        Arguments.of(
            "S15.9: enum names are case-sensitive (set)",
            body("'header':{'notes':{'operation':'set','value':'Urgent'}}")),
        Arguments.of(
            "S15.9: unknown line operation PATCH",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'PATCH','lineId':'$l1','fields':{"
                    + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]")),
        Arguments.of(
            "S15.9: unknown resolution choice FORCE",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'resolutions':[{'key':'paymentTerms','choice':'FORCE'}]")),
        Arguments.of(
            "S15.9: unknown delivery term",
            body(
                "'header':{'deliveryTerms':{'operation':'SET','value':{'term':'XYZ',"
                    + "'place':'York'}}}")),
        Arguments.of(
            "S15.9: unknown module type",
            body(
                "'header':{"
                    + NOTES_SET
                    + "},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'WOOL'}}}}]")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unknownEnums")
  @DisplayName("S15.9: an unknown enum value is a 400, read separately from unknown properties")
  void unknownEnumIsUnreadable(String scenario, String body) {
    assertThatThrownBy(() -> read(body)).isInstanceOf(JsonProcessingException.class);
  }

  static Stream<Arguments> explicitNulls() {
    return Stream.of(
        Arguments.of("S2.8, S15.11: an explicit null header key", body("'header':{'notes':null}")),
        Arguments.of("S15.11: explicit null header container", body("'header':null")),
        Arguments.of(
            "S15.11: explicit null lines container",
            body("'header':{" + NOTES_SET + "},'lines':null")),
        Arguments.of(
            "S15.11: null line operation", body("'header':{" + NOTES_SET + "},'lines':[null]")),
        Arguments.of(
            "S15.11: explicit null resolutions container",
            body("'header':{" + NOTES_SET + "},'resolutions':null")),
        Arguments.of(
            "S15.11: null resolution", body("'header':{" + NOTES_SET + "},'resolutions':[null]")),
        Arguments.of(
            "S15.11: explicit null line field container",
            body("'lines':[{'operation':'UPDATE','lineId':'$l1','fields':null}]")),
        Arguments.of(
            "S15.11: explicit null line field key",
            body("'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{'pricing':null}}]")),
        Arguments.of(
            "S15.11: explicit null field operation",
            body("'header':{'notes':{'operation':null,'value':'Urgent'}}")),
        Arguments.of(
            "S15.11: explicit null line operation",
            body(
                "'lines':[{'operation':null,'lineId':'$l1','fields':{"
                    + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("explicitNulls")
  @DisplayName("S2.8, S15.11: an explicit null container or instruction is a 400, never absence")
  void explicitNullIsUnreadable(String scenario, String body) {
    assertUnreadable(body);
  }

  @Test
  @DisplayName("S15.10: a new free key inside moduleSpecs is accepted as part of the value")
  void freeModuleSpecsAreAccepted() throws Exception {
    Parsed parsed =
        parse(
            body(
                "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'FABRIC',"
                    + "'moduleSpecs':{'gsm':120,'newKey':1}}}}}]"));

    FieldInstruction instruction =
        parsed.lines().getFirst().fields().get(OrderEditKey.LINE_SPECIFICATION);
    assertThat(instruction.clear()).isFalse();
    assertThat(((SalesOrderLineSpecificationValue) instruction.value()).moduleSpecs())
        .containsEntry("gsm", 120)
        .containsEntry("newKey", 1);
  }

  @Test
  @DisplayName("S15.18: a free sourceValue of an unmodelled constraint is accepted in a request")
  void freeSourceValueIsAcceptedInARequest() throws Exception {
    String profile =
        PROFILE.replace(
            "'sales-req-v1'}",
            "'sales-req-v1','unmodelledConstraints':[{'field':'blendTolerance',"
                + "'status':'AMBIGUOUS_MEANING','sourceValue':{'anyKey':[1,{'nested':true}],"
                + "'x':null}}]}");

    Parsed parsed =
        parse(
            body(
                "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                    + "'specification':{'operation':'SET','value':{'moduleType':'FABRIC',"
                    + "'requirementProfile':"
                    + profile
                    + "}}}}]"));

    SalesOrderLineSpecificationValue value =
        (SalesOrderLineSpecificationValue)
            parsed.lines().getFirst().fields().get(OrderEditKey.LINE_SPECIFICATION).value();
    JsonNode sourceValue =
        value.requirementProfile().unmodelledConstraints().getFirst().sourceValue();
    assertThat(sourceValue)
        .isEqualTo(mapper.readTree(json("{'anyKey':[1,{'nested':true}],'x':null}")));
  }

  // ── 400: the requirement-profile input read on its own ─────────────────────

  @Nested
  class RequirementProfileInputStrictness {

    @Test
    @DisplayName("S15.16: today's valid profile input with every valueType reads back unchanged")
    void validProfileInputRoundTrips() throws Exception {
      RequirementProfileInput input = everyValueType();

      // Through JSON text, as a request arrives; a tree would strip decimal trailing zeros.
      RequirementProfileInput read =
          mapper.readValue(mapper.writeValueAsString(input), RequirementProfileInput.class);

      assertThat(read).isEqualTo(input);
    }

    @Test
    @DisplayName("S15.17: an unknown property of a facet is a 400")
    void unknownFacetPropertyIsUnreadable() {
      ObjectNode tree = mapper.valueToTree(everyValueType());
      ((ObjectNode) tree.get("facets").get(0)).put("extra", 1);

      assertThatThrownBy(() -> mapper.treeToValue(tree, RequirementProfileInput.class))
          .isInstanceOf(JsonProcessingException.class)
          .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "S15.15/S15.17 (R3): every valueType reads its valid value and refuses it with an unknown"
            + " property")
    void everyValueTypeIsClosed() throws Exception {
      List<RequirementFacetValue> values =
          everyValueType().facets().stream().map(facet -> facet.value()).toList();
      assertThat(values).hasSize(10);
      for (RequirementFacetValue value : values) {
        String text = mapper.writeValueAsString(value);
        assertThat(mapper.readTree(text).has("valueType")).as(text).isTrue();
        assertThat(mapper.readValue(text, RequirementFacetValue.class)).isEqualTo(value);

        ObjectNode extended = (ObjectNode) mapper.readTree(text);
        extended.put("unexpected", 1);
        assertThatThrownBy(
                () ->
                    mapper.readValue(
                        mapper.writeValueAsString(extended), RequirementFacetValue.class))
            .as("%s with an unknown property", value.getClass().getSimpleName())
            .isInstanceOf(JsonProcessingException.class);
      }
    }

    @Test
    @DisplayName("S15.17: an unknown property of a facet value is a 400")
    void unknownFacetValuePropertyIsUnreadable() {
      ObjectNode tree = mapper.valueToTree(everyValueType());
      ((ObjectNode) tree.get("facets").get(0).get("value")).put("extra", 1);

      assertThatThrownBy(() -> mapper.treeToValue(tree, RequirementProfileInput.class))
          .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("S15.18: a free sourceValue is accepted and kept verbatim")
    void freeSourceValueIsKeptVerbatim() throws Exception {
      JsonNode free = mapper.readTree(json("{'anyKey':[1,{'nested':true}],'x':null}"));
      ObjectNode tree = mapper.valueToTree(everyValueType());
      ObjectNode constraint = tree.putArray("unmodelledConstraints").addObject();
      constraint.put("field", "blendTolerance");
      constraint.put("status", "RESOLVED_UNSUPPORTED");
      constraint.set("sourceValue", free);
      constraint.put("meaning", "declared blend tolerance");

      RequirementProfileInput read = mapper.treeToValue(tree, RequirementProfileInput.class);

      UnmodelledSpecConstraint kept = read.unmodelledConstraints().getFirst();
      assertThat(kept.sourceValue()).isEqualTo(free);
      assertThat(mapper.writeValueAsString(kept.sourceValue()))
          .isEqualTo(json("{'anyKey':[1,{'nested':true}],'x':null}"));
    }

    @Test
    @DisplayName("S15.19: an unknown outer property of an unmodelled constraint is a 400")
    void unknownConstraintPropertyIsUnreadable() {
      ObjectNode tree = mapper.valueToTree(everyValueType());
      ObjectNode constraint = tree.putArray("unmodelledConstraints").addObject();
      constraint.put("field", "blendTolerance");
      constraint.put("status", "AMBIGUOUS_MEANING");
      constraint.put("origin", "customer e-mail");

      assertThatThrownBy(() -> mapper.treeToValue(tree, RequirementProfileInput.class))
          .isInstanceOf(JsonProcessingException.class)
          .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  // ── Bean Validation ────────────────────────────────────────────────────────

  @Nested
  class BeanValidation {

    @Test
    @DisplayName("S2.6: a valid request has no constraint violation")
    void validRequestHasNoViolation() throws Exception {
      assertThat(validator.validate(read(body("'header':{" + NOTES_SET + "}")))).isEmpty();
    }

    @Test
    @DisplayName("S2.6: a missing operation id and a missing field operation are violations")
    void missingRequiredPartsAreViolations() throws Exception {
      SalesOrderEditRequest request =
          read(json("{'baseId':'$base','header':{'notes':{'value':'Urgent'}}}"));

      assertThat(paths(validator.validate(request)))
          .contains("operationId", "header.notes.operation");
      assertUnprocessable(request, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("S4.6, S2.9: nested value constraints are validated through every level")
    void nestedValuesAreValidated() throws Exception {
      SalesOrderEditRequest request =
          read(
              body(
                  "'header':{'contact':{'operation':'SET','value':{'name':'Jane Hill',"
                      + "'email':'not-an-email','whatsapp':false}}},"
                      + "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'quantity':{'operation':'SET','value':{'requestedQty':0,'unit':'M'}},"
                      + "'pricing':{'operation':'SET','value':{'currency':'gbp',"
                      + "'unitPrice':-1}}}}]"));

      assertThat(paths(validator.validate(request)))
          .contains(
              "header.contact.value.email",
              "lines[0].fields.quantity.value.requestedQty",
              "lines[0].fields.pricing.value.currency",
              "lines[0].fields.pricing.value.unitPrice");
    }
  }

  // ── 422: SET / CLEAR rules ─────────────────────────────────────────────────

  @Nested
  class FieldRules {

    @Test
    @DisplayName("S2.3: orderDate CLEAR is REQUIRED_FIELD_CANNOT_BE_CLEARED with key orderDate")
    void requiredHeaderKeyCannotBeCleared() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body("'header':{'orderDate':{'operation':'CLEAR'}}"),
              "REQUIRED_FIELD_CANNOT_BE_CLEARED");

      assertThat(failure.getDetails()).containsEntry("key", "orderDate");
    }

    @Test
    @DisplayName("S2.5: a blank text with SET is BLANK_VALUE_USE_CLEAR")
    void blankTextIsRefused() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body("'header':{'notes':{'operation':'SET','value':'  '}}"), "BLANK_VALUE_USE_CLEAR");

      assertThat(failure.getDetails()).containsEntry("key", "notes");
    }

    @Test
    @DisplayName("S2.5: a composite value whose every part is empty is BLANK_VALUE_USE_CLEAR")
    void blankCompositeIsRefused() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'pricing':{'operation':'SET','value':{}}}}]"),
              "BLANK_VALUE_USE_CLEAR");

      assertThat(failure.getDetails())
          .containsEntry("key", "line.pricing")
          .containsEntry("lineId", L1);
    }

    @Test
    @DisplayName("S2.6, S15.13: SET without a value and SET with null are SET_REQUIRES_VALUE")
    void setRequiresAValue() throws Exception {
      DomainException absent =
          assertUnprocessable(body("'header':{'notes':{'operation':'SET'}}"), "SET_REQUIRES_VALUE");
      DomainException explicitNull =
          assertUnprocessable(
              body("'header':{'notes':{'operation':'SET','value':null}}"), "SET_REQUIRES_VALUE");

      assertThat(absent.getDetails()).containsEntry("key", "notes");
      assertThat(explicitNull.getDetails()).containsEntry("key", "notes");
    }

    @Test
    @DisplayName("S2.7, S15.12: CLEAR with a value, even null, is CLEAR_TAKES_NO_VALUE")
    void clearTakesNoValue() throws Exception {
      DomainException withText =
          assertUnprocessable(
              body("'header':{'notes':{'operation':'CLEAR','value':'x'}}"), "CLEAR_TAKES_NO_VALUE");
      DomainException withNull =
          assertUnprocessable(
              body("'header':{'notes':{'operation':'CLEAR','value':null}}"),
              "CLEAR_TAKES_NO_VALUE");

      assertThat(withText.getDetails()).containsEntry("key", "notes");
      assertThat(withNull.getDetails()).containsEntry("key", "notes");
    }

    @Test
    @DisplayName(
        "S2.10: quantity CLEAR on UPDATE L1 is REQUIRED_FIELD_CANNOT_BE_CLEARED, lineId L1")
    void quantityCannotBeCleared() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'quantity':{'operation':'CLEAR'}}}]"),
              "REQUIRED_FIELD_CANNOT_BE_CLEARED");

      assertThat(failure.getDetails())
          .containsEntry("key", "line.quantity")
          .containsEntry("lineId", L1);
    }

    @Test
    @DisplayName("S12.2: singleLotRequired CLEAR is refused; SET(false) is a value, not a clear")
    void singleLotRequiredFalseIsAValue() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'singleLotRequired':{'operation':'CLEAR'}}}]"),
              "REQUIRED_FIELD_CANNOT_BE_CLEARED");
      assertThat(failure.getDetails())
          .containsEntry("key", "line.singleLotRequired")
          .containsEntry("lineId", L1);

      Parsed parsed =
          parse(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'singleLotRequired':{'operation':'SET','value':false}}}]"));
      FieldInstruction instruction =
          parsed.lines().getFirst().fields().get(OrderEditKey.LINE_SINGLE_LOT_REQUIRED);
      assertThat(instruction.clear()).isFalse();
      assertThat(instruction.value()).isEqualTo(false);
    }

    @Test
    @DisplayName("S12.7: specification CLEAR is REQUIRED_FIELD_CANNOT_BE_CLEARED")
    void specificationCannotBeCleared() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'specification':{'operation':'CLEAR'}}}]"),
              "REQUIRED_FIELD_CANNOT_BE_CLEARED");

      assertThat(failure.getDetails()).containsEntry("key", "line.specification");
    }

    @Test
    @DisplayName("S4.6: WhatsApp consent without a phone is CONTACT_WHATSAPP_NEEDS_PHONE")
    void whatsappNeedsPhone() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'header':{'contact':{'operation':'SET','value':{'name':'Jane Hill',"
                      + "'email':null,'phone':null,'whatsapp':true}}}"),
              "CONTACT_WHATSAPP_NEEDS_PHONE");

      assertThat(failure.getDetails()).containsEntry("key", "contact");
    }

    @Test
    @DisplayName("S2.9: a text longer than its column is VALIDATION_ERROR with its key")
    void textLongerThanItsColumnIsRefused() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'header':{'paymentTerms':{'operation':'SET','value':'"
                      + "x".repeat(201)
                      + "'}}"),
              "VALIDATION_ERROR");

      assertThat(failure.getDetails()).containsEntry("key", "paymentTerms");
      // R3: the request path in the problem's errors, as bean validation reports a field.
      assertThat(failure.getFieldErrors())
          .containsOnlyKeys("header.paymentTerms.value")
          .containsEntry("header.paymentTerms.value", "At most 200 characters");
      assertThat(failure.getDetails()).doesNotContainKey("errors");
    }

    @ParameterizedTest(name = "{0} up to {1} characters")
    @MethodSource("com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequestTest#textLimits")
    @DisplayName("S2.9 (R3): each limited text accepts its limit and refuses one more character")
    void eachTextLimitIsItsOwn(String key, int limit) throws Exception {
      Parsed atLimit =
          parse(
              body(
                  "'header':{'"
                      + key
                      + "':{'operation':'SET','value':'"
                      + "x".repeat(limit)
                      + "'}}"));
      assertThat(atLimit.header()).hasSize(1);

      DomainException over =
          assertUnprocessable(
              body(
                  "'header':{'"
                      + key
                      + "':{'operation':'SET','value':'"
                      + "x".repeat(limit + 1)
                      + "'}}"),
              "VALIDATION_ERROR");
      assertThat(over.getDetails()).containsEntry("key", key);
      assertThat(over.getFieldErrors()).containsOnlyKeys("header." + key + ".value");
    }

    @Test
    @DisplayName("S2.9 (R3): notes and the line description have no length limit")
    void unlimitedTextsAreAccepted() throws Exception {
      String long5000 = "x".repeat(5000);
      Parsed parsed =
          parse(
              body(
                  "'header':{'notes':{'operation':'SET','value':'"
                      + long5000
                      + "'}},'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'productDesc':{'operation':'SET','value':'"
                      + long5000
                      + "'}}}]"));
      assertThat(parsed.header()).containsKey(OrderEditKey.NOTES);
      assertThat(parsed.lines().getFirst().fields()).containsKey(OrderEditKey.LINE_PRODUCT_DESC);
    }

    @Test
    @DisplayName("§4.2 (R3): a composite's missing part names its request path")
    void missingCompositePartNamesItsPath() throws Exception {
      DomainException place =
          assertUnprocessable(
              body("'header':{'deliveryTerms':{'operation':'SET','value':{'term':'DAP'}}}"),
              "VALIDATION_ERROR");
      assertThat(place.getFieldErrors()).containsOnlyKeys("header.deliveryTerms.value.place");

      DomainException unit =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'quantity':{'operation':'SET','value':{'requestedQty':5}}}}]"),
              "VALIDATION_ERROR");
      assertThat(unit.getFieldErrors()).containsOnlyKeys("lines[0].fields.quantity.value.unit");
      assertThat(unit.getDetails())
          .containsEntry("key", "line.quantity")
          .containsEntry("lineId", L1);
    }

    @Test
    @DisplayName("S2.1: CLEAR of an optional key is a clear instruction without a value")
    void clearOfOptionalKeyIsAccepted() throws Exception {
      Parsed parsed = parse(body("'header':{'paymentTerms':{'operation':'CLEAR'}}"));

      assertThat(parsed.header())
          .containsEntry(
              OrderEditKey.PAYMENT_TERMS,
              new FieldInstruction(OrderEditKey.PAYMENT_TERMS, true, null));
    }

    @Test
    @DisplayName("S15.14: absent discount and tax read like explicit nulls (the same value)")
    void absentAndNullPartsAreTheSameValue() throws Exception {
      Parsed absent =
          parse(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'pricing':{'operation':'SET','value':{'currency':'GBP',"
                      + "'unitPrice':4}}}}]"));
      Parsed explicitNull =
          parse(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'pricing':{'operation':'SET','value':{'currency':'GBP','unitPrice':4,"
                      + "'discountAmount':null,'taxAmount':null}}}}]"));

      SalesOrderLinePricingValue value =
          (SalesOrderLinePricingValue)
              absent.lines().getFirst().fields().get(OrderEditKey.LINE_PRICING).value();
      assertThat(value.discountAmount()).isNull();
      assertThat(value.taxAmount()).isNull();
      assertThat(value)
          .isEqualTo(
              explicitNull.lines().getFirst().fields().get(OrderEditKey.LINE_PRICING).value());
      assertThat(absent.fingerprint()).isEqualTo(explicitNull.fingerprint());
    }
  }

  // ── 422: line operations ───────────────────────────────────────────────────

  @Nested
  class LineRules {

    @Test
    @DisplayName("S6.6: the same clientLineId on two ADDs is CLIENT_LINE_ID_DUPLICATED")
    void duplicateClientLineId() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body("'lines':[" + add("$c1") + "," + add("$c1") + "]"), "CLIENT_LINE_ID_DUPLICATED");

      assertThat(failure.getDetails())
          .containsEntry("key", OrderEditKey.LINE)
          .containsEntry("clientLineId", C1);
    }

    @Test
    @DisplayName("S6.7: UPDATE and REMOVE of L1 in one request is LINE_OPERATION_DUPLICATED")
    void duplicateLineOperation() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{"
                      + "'productDesc':{'operation':'SET','value':'Selvedge note'}}},"
                      + "{'operation':'REMOVE','lineId':'$l1'}]"),
              "LINE_OPERATION_DUPLICATED");

      assertThat(failure.getDetails())
          .containsEntry("key", OrderEditKey.LINE)
          .containsEntry("lineId", L1);
    }

    @Test
    @DisplayName("S6.8: CLEAR inside an ADD is CLEAR_NOT_ALLOWED_ON_ADD")
    void clearOnAddIsRefused() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'ADD','clientLineId':'$c1','productId':'$p3','fields':{"
                      + "'quantity':{'operation':'SET','value':{'requestedQty':200,'unit':'M'}},"
                      + "'pricing':{'operation':'CLEAR'}}}]"),
              "CLEAR_NOT_ALLOWED_ON_ADD");

      assertThat(failure.getDetails())
          .containsEntry("key", "line.pricing")
          .containsEntry("clientLineId", C1);
    }

    @Test
    @DisplayName("S6.9: a product id on an UPDATE operation is VALIDATION_ERROR naming the line")
    void productIdOnUpdateIsRefused() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'UPDATE','lineId':'$l1','productId':'$p3','fields':{"
                      + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]"),
              "VALIDATION_ERROR");

      assertThat(failure.getDetails()).containsEntry("lineId", L1);
      assertThat(failure.getFieldErrors()).containsOnlyKeys("lines[0].productId");
    }

    @Test
    @DisplayName("S6.1: an ADD needs its client id, its product and its quantity, and no line id")
    void addNeedsItsIdentity() throws Exception {
      DomainException noClientId =
          assertUnprocessable(
              body(
                  "'lines':["
                      + add("$c2")
                      + ",{'operation':'ADD','productId':'$p3','fields':{"
                      + "'quantity':{'operation':'SET','value':{'requestedQty':200,'unit':'M'}}}}]"),
              "VALIDATION_ERROR");
      assertThat(noClientId.getFieldErrors()).containsOnlyKeys("lines[1].clientLineId");
      DomainException noProduct =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'ADD','clientLineId':'$c1','fields':{"
                      + "'quantity':{'operation':'SET','value':{'requestedQty':200,'unit':'M'}}}}]"),
              "VALIDATION_ERROR");
      assertThat(noProduct.getFieldErrors()).containsOnlyKeys("lines[0].productId");
      DomainException withLineId =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'ADD','lineId':'$l1','clientLineId':'$c1',"
                      + "'productId':'$p3','fields':{'quantity':{'operation':'SET','value':"
                      + "{'requestedQty':200,'unit':'M'}}}}]"),
              "VALIDATION_ERROR");
      assertThat(withLineId.getFieldErrors()).containsOnlyKeys("lines[0].lineId");
      DomainException noQuantity =
          assertUnprocessable(
              body(
                  "'lines':[{'operation':'ADD','clientLineId':'$c1','productId':'$p3','fields':{"
                      + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]"),
              "VALIDATION_ERROR");
      assertThat(noQuantity.getDetails())
          .containsEntry("key", "line.quantity")
          .containsEntry("clientLineId", C1);
      assertThat(noQuantity.getFieldErrors()).containsOnlyKeys("lines[0].fields.quantity");
    }

    @Test
    @DisplayName("S7.7: a REMOVE carries only its line id; an UPDATE says what changes")
    void removeAndUpdateShapes() throws Exception {
      assertUnprocessable(
          body(
              "'lines':[{'operation':'REMOVE','lineId':'$l1','fields':{"
                  + "'productDesc':{'operation':'SET','value':'Selvedge note'}}}]"),
          "VALIDATION_ERROR");
      assertUnprocessable(
          body("'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{}}]"), "VALIDATION_ERROR");
      assertUnprocessable(body("'lines':[{'operation':'REMOVE'}]"), "VALIDATION_ERROR");

      Parsed parsed = parse(body("'lines':[{'operation':'REMOVE','lineId':'$l1'}]"));
      assertThat(parsed.lines().getFirst().operation())
          .isEqualTo(SalesOrderLineEditOperation.REMOVE);
      assertThat(parsed.lines().getFirst().lineId()).isEqualTo(L1);
    }

    @Test
    @DisplayName("S6.1: two ADDs with distinct client ids parse with their products")
    void twoAddsParse() throws Exception {
      Parsed parsed = parse(body("'lines':[" + add("$c1") + "," + add("$c2") + "]"));

      assertThat(parsed.lines())
          .extracting(SalesOrderEditInstructions.LineOperation::clientLineId)
          .containsExactly(C1, C2);
      assertThat(parsed.lines()).allSatisfy(line -> assertThat(line.productId()).isEqualTo(P3));
    }

    @Test
    @DisplayName(
        "CEDIT-02 §4.2 (no vector): more line operations than configured is VALIDATION_ERROR")
    void lineOperationLimitIsConfigured() throws Exception {
      SalesOrderEditRequest request =
          read(
              body(
                  "'lines':[{'operation':'REMOVE','lineId':'$l1'},"
                      + "{'operation':'REMOVE','lineId':'$l2'},"
                      + "{'operation':'REMOVE','lineId':'$l3'}]"));

      Throwable thrown = catchThrowable(() -> SalesOrderEditInstructions.parse(ORDER, request, 2));
      DomainException failure = unprocessable(thrown, "VALIDATION_ERROR");
      assertThat(failure.getDetails()).containsEntry("key", "lines");
      assertThat(failure.getFieldErrors()).containsOnlyKeys("lines");
      assertThat(SalesOrderEditInstructions.parse(ORDER, request, 3).lines()).hasSize(3);
    }

    @Test
    @DisplayName("CEDIT-02 §4.2 (no vector): a request without change or decision is EMPTY_EDIT")
    void emptyRequestIsRefused() throws Exception {
      assertUnprocessable(json("{'operationId':'$op','baseId':'$base'}"), "EMPTY_EDIT");
      assertUnprocessable(body("'header':{},'lines':[],'resolutions':[]"), "EMPTY_EDIT");
    }
  }

  // ── resolutions at parse level ─────────────────────────────────────────────

  @Nested
  class Resolutions {

    @Test
    @DisplayName(
        "S9.3: an empty resolution list parses; the mismatch is decided against the recorded"
            + " conflicts")
    void emptyResolutionsParse() throws Exception {
      Parsed parsed =
          parse(
              body(
                  "'header':{'paymentTerms':{'operation':'SET','value':'60 days'}},"
                      + "'resolutions':[]"));

      assertThat(parsed.resolutions()).isEmpty();
    }

    @Test
    @DisplayName("S9.1, S9.9: resolutions parse into slots with their choices")
    void resolutionsParseIntoSlots() throws Exception {
      Parsed parsed =
          parse(
              body(
                  "'header':{'paymentTerms':{'operation':'SET','value':'60 days'}},"
                      + "'resolutions':[{'key':'paymentTerms','choice':'USE_MINE'},"
                      + "{'key':'line','lineId':'$l2','choice':'KEEP_CURRENT'},"
                      + "{'key':'line','clientLineId':'$c1','choice':'NEW_VALUE'}]"));

      assertThat(parsed.resolutions())
          .containsEntry(Slot.header(OrderEditKey.PAYMENT_TERMS), Choice.USE_MINE)
          .containsEntry(Slot.wholeLine(L2), Choice.KEEP_CURRENT)
          .containsEntry(Slot.addedLine(C1), Choice.NEW_VALUE);
      Parsed onlyKeep =
          parse(body("'resolutions':[{'key':'paymentTerms','choice':'KEEP_CURRENT'}]"));
      assertThat(onlyKeep.header()).isEmpty();
      assertThat(onlyKeep.resolutions()).hasSize(1);
    }

    @Test
    @DisplayName("S9.3: the same conflict decided twice is RESOLUTION_MISMATCH")
    void duplicateResolutionIsAMismatch() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body(
                  "'resolutions':[{'key':'paymentTerms','choice':'USE_MINE'},"
                      + "{'key':'paymentTerms','choice':'KEEP_CURRENT'}]"),
              "RESOLUTION_MISMATCH");

      assertThat(failure.getDetails()).containsEntry("key", "paymentTerms");
    }

    @Test
    @DisplayName("S9.3: a resolution for a key that does not exist is RESOLUTION_MISMATCH")
    void unknownResolutionKeyIsAMismatch() throws Exception {
      DomainException failure =
          assertUnprocessable(
              body("'resolutions':[{'key':'discountNote','choice':'KEEP_CURRENT'}]"),
              "RESOLUTION_MISMATCH");

      assertThat(failure.getDetails()).containsEntry("key", "discountNote");
    }
  }

  // ── fingerprint ────────────────────────────────────────────────────────────

  @Nested
  class Fingerprint {

    @Test
    @DisplayName("S10.2: the same content gives the same fingerprint whatever the JSON order")
    void sameContentSameFingerprint() throws Exception {
      String first =
          json(
              "{'operationId':'$op','baseId':'$base','header':{'notes':{'operation':'SET',"
                  + "'value':'Urgent'},'paymentTerms':{'operation':'SET','value':'60 days'}}}");
      String reordered =
          json(
              "{ 'header' : { 'paymentTerms':{'value':'60 days','operation':'SET'},"
                  + " 'notes':{'value':'Urgent','operation':'SET'} },"
                  + " 'baseId':'$base', 'operationId':'$op' }");

      assertThat(parseJson(first).fingerprint()).isEqualTo(parseJson(reordered).fingerprint());
      assertThat(parseJson(first).fingerprint()).isEqualTo(parseJson(first).fingerprint());
    }

    @Test
    @DisplayName("S10.4: the fingerprint differs by order id for the same operation and content")
    void fingerprintIncludesTheOrder() throws Exception {
      SalesOrderEditRequest request = read(body("'header':{" + NOTES_SET + "}"));

      assertThat(
              SalesOrderEditInstructions.parse(ORDER, request, MAX_LINE_OPERATIONS).fingerprint())
          .isNotEqualTo(
              SalesOrderEditInstructions.parse(OTHER_ORDER, request, MAX_LINE_OPERATIONS)
                  .fingerprint());
    }

    @Test
    @DisplayName("S12.2, S12.3: SET(false), absence and CLEAR, zero and CLEAR never hash alike")
    void intentKeepsItsShapeInTheFingerprint() throws Exception {
      String update = "'lines':[{'operation':'UPDATE','lineId':'$l1','fields':{%s}}]";
      String desc = "'productDesc':{'operation':'SET','value':'Selvedge note'}";
      long distinct =
          Stream.of(
                  parse(body(update.formatted(desc))).fingerprint(),
                  parse(
                          body(
                              update.formatted(
                                  desc + ",'singleLotRequired':{'operation':'SET','value':false}")))
                      .fingerprint(),
                  parse(
                          body(
                              update.formatted(
                                  desc
                                      + ",'tolerance':{'operation':'SET','value':"
                                      + "{'upPct':0,'downPct':0}}")))
                      .fingerprint(),
                  parse(body(update.formatted(desc + ",'tolerance':{'operation':'CLEAR'}")))
                      .fingerprint(),
                  parse(body("'header':{'notes':{'operation':'CLEAR'}}")).fingerprint(),
                  parse(body("'header':{'notes':{'operation':'SET','value':'x'}}")).fingerprint(),
                  parse(body("'header':{'paymentTerms':{'operation':'SET','value':'x'}}"))
                      .fingerprint())
              .distinct()
              .count();

      assertThat(distinct).isEqualTo(7);
    }

    @Test
    @DisplayName("S10.4: another resolution choice is another fingerprint")
    void resolutionsArePartOfTheFingerprint() throws Exception {
      String keep = body("'resolutions':[{'key':'paymentTerms','choice':'KEEP_CURRENT'}]");
      String mine =
          body(
              "'header':{'paymentTerms':{'operation':'SET','value':'60 days'}},"
                  + "'resolutions':[{'key':'paymentTerms','choice':'USE_MINE'}]");
      String newValue = mine.replace("USE_MINE", "NEW_VALUE");

      assertThat(
              Stream.of(parse(keep), parse(mine), parse(newValue))
                  .map(Parsed::fingerprint)
                  .distinct()
                  .count())
          .isEqualTo(3);
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** A body with the fixed operation and base ids and the given members. */
  private static String body(String members) {
    return "{'operationId':'$op','baseId':'$base'," + members + "}";
  }

  private static String add(String clientLineId) {
    return "{'operation':'ADD','clientLineId':'"
        + clientLineId
        + "','productId':'$p3','fields':{'quantity':{'operation':'SET','value':"
        + "{'requestedQty':200,'unit':'M'}}}}";
  }

  /** Real JSON: double quotes and the fixed ids. */
  private static String json(String template) {
    return template
        .replace('\'', '"')
        .replace("$op", OPERATION.toString())
        .replace("$base", BASE.toString())
        .replace("$l1", L1.toString())
        .replace("$l2", L2.toString())
        .replace("$l3", L3.toString())
        .replace("$c1", C1.toString())
        .replace("$c2", C2.toString())
        .replace("$p3", P3.toString())
        .replace("$actor", ACTOR.toString());
  }

  private SalesOrderEditRequest read(String template) throws JsonProcessingException {
    return mapper.readValue(json(template), SalesOrderEditRequest.class);
  }

  private Parsed parse(String template) throws JsonProcessingException {
    return SalesOrderEditInstructions.parse(ORDER, read(template), MAX_LINE_OPERATIONS);
  }

  private Parsed parseJson(String json) throws JsonProcessingException {
    return SalesOrderEditInstructions.parse(
        ORDER, mapper.readValue(json, SalesOrderEditRequest.class), MAX_LINE_OPERATIONS);
  }

  private void assertUnreadable(String template) {
    assertThatThrownBy(() -> read(template))
        .isInstanceOf(JsonProcessingException.class)
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  /** Reads the body (it must be readable) and expects a 422 with {@code code} from the parse. */
  private DomainException assertUnprocessable(String template, String code) throws Exception {
    return assertUnprocessable(read(template), code);
  }

  private DomainException assertUnprocessable(SalesOrderEditRequest request, String code) {
    return unprocessable(
        catchThrowable(() -> SalesOrderEditInstructions.parse(ORDER, request, MAX_LINE_OPERATIONS)),
        code);
  }

  private static DomainException unprocessable(Throwable thrown, String code) {
    assertThat(thrown).isInstanceOf(DomainException.class);
    DomainException failure = (DomainException) thrown;
    assertThat(failure.getHttpStatus()).isEqualTo(422);
    assertThat(failure.getErrorCode()).isEqualTo(code);
    return failure;
  }

  private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
  }

  /** The header texts with their column limit (CEDIT-02 §4.6, S2.9). */
  static Stream<Arguments> textLimits() {
    return Stream.of(
        Arguments.of("customerReference", 100),
        Arguments.of("paymentTerms", 200),
        Arguments.of("shippingAddress", 500),
        Arguments.of("billingAddress", 500),
        Arguments.of("shippingMethod", 50));
  }

  /** A valid profile input with one facet of every valueType (S15.16). */
  private static RequirementProfileInput everyValueType() {
    return RequirementProfileSamples.everyValueType(ACTOR, C1);
  }
}
