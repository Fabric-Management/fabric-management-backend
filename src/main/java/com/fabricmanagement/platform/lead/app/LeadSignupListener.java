package com.fabricmanagement.platform.lead.app;

import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadSignupListener {

  private final LeadService leadService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSelfSignupCompleted(SelfSignupCompletedEvent event) {
    try {
      leadService.captureFromSignup(
          event.getOrganizationName(),
          event.getTaxId(),
          parseOrganizationType(event.getOrganizationType()),
          event.getFirstName(),
          event.getLastName(),
          event.getRecipientEmail(),
          event.getSubscriptionOsCodes(),
          event.getSignupIntent(),
          event.getTrialTenantId());
      log.info(
          "Lead captured from signup: recipient={}, trialTenantId={}",
          maskEmail(event.getRecipientEmail()),
          event.getTrialTenantId());
    } catch (Exception ex) {
      log.error(
          "Failed to capture signup lead: recipient={}, trialTenantId={}, error={}",
          maskEmail(event.getRecipientEmail()),
          event.getTrialTenantId(),
          ex.getMessage(),
          ex);
    }
  }

  private OrganizationType parseOrganizationType(String organizationType) {
    return OrganizationType.valueOf(organizationType);
  }

  private String maskEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.replaceAll("(.).*@", "$1***@");
  }
}
