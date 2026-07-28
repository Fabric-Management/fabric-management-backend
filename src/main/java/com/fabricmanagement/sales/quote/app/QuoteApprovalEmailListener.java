package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.communication.api.facade.CustomerEmailFacade;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.event.QuoteApprovalTokenGeneratedEvent;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends customer quote approval emails after token creation commits. */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteApprovalEmailListener {

  private final CustomerEmailFacade customerEmailFacade;
  private final LocalizationService localizationService;
  private final FrontendUrlProvider frontendUrlProvider;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onQuoteApprovalTokenGenerated(QuoteApprovalTokenGeneratedEvent event) {
    if (event.getChannel() != QuoteApprovalChannel.EMAIL) {
      return;
    }

    Locale locale = Locale.forLanguageTag(event.getLocaleLanguageTag());
    String approvalUrl = frontendUrlProvider.buildUrl("/quotes/approve/" + event.getToken());
    String subject =
        localizationService.getMessage(
            "email.quote.approval.subject", new Object[] {event.getQuoteNumber()}, locale);
    String heading = localizationService.getMessage("email.quote.approval.heading", null, locale);
    String body =
        localizationService.getMessage(
            "email.quote.approval.body", new Object[] {event.getQuoteNumber()}, locale);
    String cta = localizationService.getMessage("email.quote.approval.cta", null, locale);
    String expires = localizationService.getMessage("email.quote.approval.expires", null, locale);

    customerEmailFacade.sendQuoteApprovalEmail(
        event.getTenantId(),
        event.getCustomerEmail(),
        subject,
        heading,
        body,
        cta,
        expires,
        approvalUrl);

    log.info(
        "Quote approval email sent: quoteId={}, quoteNumber={}",
        event.getQuoteId(),
        event.getQuoteNumber());
  }
}
