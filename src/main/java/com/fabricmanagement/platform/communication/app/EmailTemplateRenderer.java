package com.fabricmanagement.platform.communication.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Email Template Renderer - Backend template rendering service.
 *
 * <p>Simple wrapper around {@link EmailTemplateService} for consistent API. Uses backend HTML
 * templates from {@code resources/templates/emails/}.
 *
 * <p><b>Note:</b> Frontend template rendering was removed. All templates are now rendered directly
 * from backend HTML files.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateRenderer {

  private final EmailTemplateService emailTemplateService;

  /**
   * Render email template with variables.
   *
   * @param templateName Template name (e.g., "self-service-welcome.html")
   * @param variables Template variables
   * @return Rendered HTML
   */
  public String render(String templateName, Map<String, String> variables) {
    return emailTemplateService.render(templateName, variables);
  }

  // Convenience methods for common templates

  /** Render setup-password email. */
  public String renderSetupPassword(
      String firstName, String companyName, String email, String setupUrl) {
    Map<String, String> vars =
        Map.of(
            "firstName", firstName != null ? firstName : "there",
            "companyName", companyName != null ? companyName : "",
            "email", email != null ? email : "",
            "setupUrl", setupUrl);
    return render("self-service-welcome.html", vars);
  }

  /** Render welcome email. */
  public String renderWelcome(
      String firstName, String companyName, String subscriptionsList, String setupUrl) {
    Map<String, String> vars =
        Map.of(
            "firstName", firstName != null ? firstName : "there",
            "companyName", companyName != null ? companyName : "",
            "subscriptionsList", subscriptionsList != null ? subscriptionsList : "",
            "setupUrl", setupUrl);
    return render("sales-led-welcome.html", vars);
  }

  /** Render partner portal invitation email. */
  public String renderPartnerInvitation(
      String firstName, String partnerDisplayName, String email, String setupUrl) {
    Map<String, String> vars =
        Map.of(
            "firstName", firstName != null ? firstName : "there",
            "partnerDisplayName", partnerDisplayName != null ? partnerDisplayName : "",
            "email", email != null ? email : "",
            "setupUrl", setupUrl);
    return render("partner-invitation.html", vars);
  }

  /** Render quote customer approval email. */
  public String renderQuoteApproval(
      String heading, String body, String cta, String expires, String approvalUrl) {
    Map<String, String> vars =
        Map.of(
            "heading", heading != null ? heading : "",
            "body", body != null ? body : "",
            "cta", cta != null ? cta : "",
            "expires", expires != null ? expires : "",
            "approvalUrl", approvalUrl != null ? approvalUrl : "");
    return render("quote-approval.html", vars);
  }

  /** Render added-to-organization email for existing login identities. */
  public String renderAddedToOrganization(String firstName, String orgName, String email) {
    Map<String, String> vars =
        Map.of(
            "firstName", firstName != null && !firstName.isBlank() ? firstName : "there",
            "orgName", orgName != null && !orgName.isBlank() ? orgName : "your organization",
            "email", email != null ? email : "");
    return render("added-to-organization.html", vars);
  }

  /** Render password reset email. */
  public String renderPasswordReset(
      String firstName, String resetUrl, String expiresIn, String verificationCode) {
    Map<String, String> vars = new java.util.HashMap<>();
    vars.put("firstName", firstName != null ? firstName : "there");
    vars.put("resetUrl", resetUrl);
    vars.put("expiresIn", expiresIn != null ? expiresIn : "30 minutes");

    // Verification code is optional - only include if provided
    if (verificationCode != null && !verificationCode.isEmpty()) {
      String codeHtml =
          "<div style='margin-top: 16px; padding: 16px; background-color: #ffffff; border: 1px solid #E5E7EB; border-radius: 8px; text-align: center;'>"
              + "<p style='font-size: 12px; color: #6b7280; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 0.5px;'>Verification Code</p>"
              + "<p style='font-size: 32px; font-weight: 700; color: #111827; letter-spacing: 4px; margin: 0; font-family: monospace;'>"
              + verificationCode
              + "</p>"
              + "</div>";
      vars.put("verificationCode", codeHtml);
    } else {
      vars.put("verificationCode", "");
    }

    return render("password-reset.html", vars);
  }
}
