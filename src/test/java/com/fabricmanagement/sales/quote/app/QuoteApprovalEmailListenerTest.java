package com.fabricmanagement.sales.quote.app;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.event.QuoteApprovalTokenGeneratedEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteApprovalEmailListenerTest {

  @Mock private NotificationService notificationService;
  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private LocalizationService localizationService;
  @Mock private FrontendUrlProvider frontendUrlProvider;

  @Test
  void rendersApprovalLinkAndSendsEmailToCustomer() {
    QuoteApprovalEmailListener listener =
        new QuoteApprovalEmailListener(
            notificationService, emailTemplateRenderer, localizationService, frontendUrlProvider);
    QuoteApprovalTokenGeneratedEvent event =
        new QuoteApprovalTokenGeneratedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Q-2026-001",
            "token-123",
            "buyer@example.com",
            QuoteApprovalChannel.EMAIL,
            "en");

    when(frontendUrlProvider.buildUrl("/quotes/approve/token-123"))
        .thenReturn("https://app.example.com/quotes/approve/token-123");
    when(localizationService.getMessage(
            eq("email.quote.approval.subject"),
            argThat(args -> Arrays.equals(args, new Object[] {"Q-2026-001"})),
            eq(Locale.ENGLISH)))
        .thenReturn("Quote Q-2026-001 is ready for approval");
    when(localizationService.getMessage("email.quote.approval.heading", null, Locale.ENGLISH))
        .thenReturn("Quote ready for approval");
    when(localizationService.getMessage(
            eq("email.quote.approval.body"),
            argThat(args -> Arrays.equals(args, new Object[] {"Q-2026-001"})),
            eq(Locale.ENGLISH)))
        .thenReturn("Quote Q-2026-001 is ready for your review and approval.");
    when(localizationService.getMessage("email.quote.approval.cta", null, Locale.ENGLISH))
        .thenReturn("Review and approve quote");
    when(localizationService.getMessage("email.quote.approval.expires", null, Locale.ENGLISH))
        .thenReturn("This approval link will expire in 7 days.");
    when(emailTemplateRenderer.renderQuoteApproval(
            "Quote ready for approval",
            "Quote Q-2026-001 is ready for your review and approval.",
            "Review and approve quote",
            "This approval link will expire in 7 days.",
            "https://app.example.com/quotes/approve/token-123"))
        .thenReturn("email body");

    listener.onQuoteApprovalTokenGenerated(event);

    verify(emailTemplateRenderer)
        .renderQuoteApproval(
            "Quote ready for approval",
            "Quote Q-2026-001 is ready for your review and approval.",
            "Review and approve quote",
            "This approval link will expire in 7 days.",
            "https://app.example.com/quotes/approve/token-123");
    verify(notificationService)
        .sendNotificationSync(
            "buyer@example.com", "Quote Q-2026-001 is ready for approval", "email body");
  }
}
