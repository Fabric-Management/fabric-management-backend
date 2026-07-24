package com.fabricmanagement.sales.quote.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuoteRequestValidationTest {

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
  void quoteCreateRequestRejectsHeaderFieldLimitViolations() {
    QuoteCreateRequest request = validCreateRequest();
    request.setPaymentTerms("x".repeat(51));
    request.setLeadTimeDays(-1);
    request.setNotes("x".repeat(2001));

    assertThat(violatedFields(request)).contains("paymentTerms", "leadTimeDays", "notes");

    request.setLeadTimeDays(3651);

    assertThat(violatedFields(request)).contains("leadTimeDays");
  }

  @Test
  void updateQuoteRequestRejectsHeaderFieldLimitViolations() {
    UpdateQuoteRequest request = new UpdateQuoteRequest();
    request.setValidUntil(LocalDate.now().plusDays(1));
    request.setCustomerId(UUID.randomUUID());
    request.setCurrency("usd");
    request.setPaymentTerms("x".repeat(51));
    request.setLeadTimeDays(-1);
    request.setNotes("x".repeat(2001));

    assertThat(violatedFields(request))
        .contains("currency", "paymentTerms", "leadTimeDays", "notes");

    request.setLeadTimeDays(3651);
    request.setCurrency("USD");

    assertThat(violatedFields(request)).contains("leadTimeDays");
  }

  @Test
  void quoteLineRequestsRejectOversizedUnit() {
    AddQuoteLineRequest addRequest = new AddQuoteLineRequest();
    addRequest.setProductId(UUID.randomUUID());
    addRequest.setRequestedQty(new BigDecimal("1.000"));
    addRequest.setUnit("x".repeat(21));
    addRequest.setOfferedPrice(new BigDecimal("1.00"));

    UpdateQuoteLineRequest updateRequest = new UpdateQuoteLineRequest();
    updateRequest.setRequestedQty(new BigDecimal("1.000"));
    updateRequest.setUnit("x".repeat(21));
    updateRequest.setOfferedPrice(new BigDecimal("1.00"));

    assertThat(violatedFields(addRequest)).containsExactly("unit");
    assertThat(violatedFields(updateRequest)).containsExactly("unit");
  }

  @Test
  void customerApprovalRequestRejectsAuditFieldLimitViolations() {
    CustomerApprovalRequest request = new CustomerApprovalRequest();
    request.setToken("token");
    request.setIpAddress("x".repeat(46));
    request.setUserAgent("x".repeat(513));

    assertThat(violatedFields(request)).contains("ipAddress", "userAgent");
  }

  @Test
  void generateQuoteTokenRequestRejectsOversizedRecipient() {
    GenerateQuoteTokenRequest request = new GenerateQuoteTokenRequest();
    request.setChannel(QuoteApprovalChannel.EMAIL);
    request.setSentTo("x".repeat(255));

    assertThat(violatedFields(request)).containsExactly("sentTo");
  }

  @Test
  void sendQuoteRequestRequiresContactId() {
    SendQuoteRequest request = new SendQuoteRequest();

    assertThat(violatedFields(request)).containsExactly("contactId");

    request.setContactId(UUID.randomUUID());

    assertThat(violatedFields(request)).isEmpty();
  }

  @Test
  void boundaryValuesPassValidation() {
    QuoteCreateRequest createRequest = validCreateRequest();
    createRequest.setPaymentTerms("x".repeat(50));
    createRequest.setLeadTimeDays(3650);
    createRequest.setNotes("x".repeat(2000));

    UpdateQuoteRequest updateRequest = new UpdateQuoteRequest();
    updateRequest.setCustomerId(UUID.randomUUID());
    updateRequest.setCurrency("GBP");
    updateRequest.setPaymentTerms("x".repeat(50));
    updateRequest.setLeadTimeDays(3650);
    updateRequest.setNotes("x".repeat(2000));

    AddQuoteLineRequest addLineRequest = new AddQuoteLineRequest();
    addLineRequest.setProductId(UUID.randomUUID());
    addLineRequest.setRequestedQty(new BigDecimal("1.000"));
    addLineRequest.setUnit("x".repeat(20));
    addLineRequest.setOfferedPrice(new BigDecimal("1.00"));

    UpdateQuoteLineRequest updateLineRequest = new UpdateQuoteLineRequest();
    updateLineRequest.setRequestedQty(new BigDecimal("1.000"));
    updateLineRequest.setUnit("x".repeat(20));
    updateLineRequest.setOfferedPrice(new BigDecimal("1.00"));

    CustomerApprovalRequest approvalRequest = new CustomerApprovalRequest();
    approvalRequest.setToken("token");
    approvalRequest.setIpAddress("x".repeat(45));
    approvalRequest.setUserAgent("x".repeat(512));

    GenerateQuoteTokenRequest tokenRequest = new GenerateQuoteTokenRequest();
    tokenRequest.setChannel(QuoteApprovalChannel.EMAIL);
    tokenRequest.setSentTo("x".repeat(254));

    assertThat(violatedFields(createRequest)).isEmpty();
    assertThat(violatedFields(updateRequest)).isEmpty();
    assertThat(violatedFields(addLineRequest)).isEmpty();
    assertThat(violatedFields(updateLineRequest)).isEmpty();
    assertThat(violatedFields(approvalRequest)).isEmpty();
    assertThat(violatedFields(tokenRequest)).isEmpty();
  }

  private static QuoteCreateRequest validCreateRequest() {
    QuoteCreateRequest request = new QuoteCreateRequest();
    request.setCustomerId(UUID.randomUUID());
    request.setAssignedToId(UUID.randomUUID());
    request.setModuleType("FABRIC");
    request.setQuoteNumber("Q-2026-001");
    request.setCurrency("GBP");
    request.setValidUntil(LocalDate.now().plusDays(1));
    return request;
  }

  @Test
  void assignedToIdIsAnOptionalOwnerOverride() {
    QuoteCreateRequest request = validCreateRequest();
    request.setAssignedToId(null);

    assertThat(violatedFields(request)).doesNotContain("assignedToId");
  }

  private static Set<String> violatedFields(Object request) {
    return validator.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
