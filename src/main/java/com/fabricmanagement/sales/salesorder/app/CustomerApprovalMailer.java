package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fabricmanagement.platform.communication.api.facade.CustomerEmailFacade;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The messages the customer's contact receives about an order: the draft's details, the request to
 * approve the evaluated version, and the one-time code. They are written in English and queued in
 * the caller's transaction, so a rolled-back step sends nothing.
 */
@Component
@RequiredArgsConstructor
public class CustomerApprovalMailer {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm 'UTC'", Locale.UK).withZone(ZoneOffset.UTC);

  private final CustomerEmailFacade email;
  private final FrontendUrlProvider urls;

  /** The draft's details for the customer's information; there is nothing to approve. */
  public void sendInformation(
      UUID tenantId, String recipient, String recipientName, OrderVersionContent content) {
    String body =
        greeting(recipientName)
            + "<p>Here are the details we have noted for your order. This is a draft for your"
            + " information: it is not a confirmation yet and there is nothing to approve. Once"
            + " our planning team has confirmed the delivery date, we will send the order for"
            + " your approval.</p>"
            + details(content);
    email.sendOrderMessage(
        tenantId,
        recipient,
        "Order " + content.orderNumber() + ": draft details for your information",
        content.sellerName(),
        "Draft details of order " + content.orderNumber(),
        body,
        null,
        null,
        "If anything here is not what you expect, please reply to your sales contact.");
  }

  /** The evaluated version, with the link to review and approve it. */
  public void sendApprovalRequest(
      UUID tenantId,
      String recipient,
      String recipientName,
      OrderVersionContent content,
      String token,
      Instant linkExpiresAt) {
    String body =
        greeting(recipientName)
            + "<p>Our planning team has evaluated your order. Please review it and approve it, or"
            + " tell us what should change. When you open the order we will send a one-time code"
            + " to this e-mail address to confirm it is you.</p>"
            + details(content);
    email.sendOrderMessage(
        tenantId,
        recipient,
        "Order " + content.orderNumber() + " is ready for your approval",
        content.sellerName(),
        "Please review and approve order " + content.orderNumber(),
        body,
        "Review the order",
        urls.buildUrl(AppRoutes.orderApproval(token)),
        "This link is valid until " + DATE_TIME.format(linkExpiresAt) + ".");
  }

  /** The one-time code that opens the approval page. */
  public void sendCode(
      UUID tenantId, String recipient, String orderNumber, String sellerName, String code) {
    String body =
        "<p>Use this code to open order "
            + escape(orderNumber)
            + ":</p><p style=\"font-size: 32px; font-weight: 700; letter-spacing: 6px;"
            + " font-family: monospace; color: #111827; margin: 16px 0;\">"
            + escape(code)
            + "</p><p>The code is valid for "
            + com.fabricmanagement.sales.salesorder.domain.CustomerApproval.CODE_TTL.toMinutes()
            + " minutes.</p>";
    email.sendOrderMessage(
        tenantId,
        recipient,
        "Your code for order " + orderNumber,
        sellerName,
        "Your one-time code",
        body,
        null,
        null,
        "If you did not ask for this code, you can ignore this e-mail.");
  }

  private static String greeting(String name) {
    return "<p>Dear " + escape(name == null || name.isBlank() ? "customer" : name) + ",</p>";
  }

  /** The version's content as a compact table, every value escaped. */
  static String details(OrderVersionContent content) {
    StringBuilder html = new StringBuilder();
    html.append("<table style=\"width: 100%; border-collapse: collapse; font-size: 14px;\">");
    row(html, "Order", content.orderNumber());
    row(html, "Customer", content.customerName());
    row(html, "Your reference", content.customerReference());
    row(html, "Requested delivery", date(content.requestedDeliveryDate()));
    OrderVersionContent.Terms terms = content.delivery();
    if (terms != null && terms.term() != null) {
      row(
          html,
          "Delivery term",
          terms.term() + " " + terms.place() + " (" + edition(terms.version()) + ")");
    }
    if (content.proposal() != null) {
      row(
          html,
          "Proposed date",
          date(content.proposal().proposedOn()) + " (" + event(content.proposal().event()) + ")");
    }
    row(html, "Payment terms", content.paymentTerms());
    html.append("</table>");
    if (!content.lines().isEmpty()) {
      html.append(
          "<table style=\"width: 100%; border-collapse: collapse; font-size: 14px; margin-top:"
              + " 16px;\"><tr>");
      for (String heading : new String[] {"Product", "Quantity", "Unit price"}) {
        html.append(
                "<th style=\"text-align: left; border-bottom: 1px solid #e5e7eb; padding: 6px"
                    + " 4px;\">")
            .append(heading)
            .append("</th>");
      }
      html.append("</tr>");
      for (OrderVersionContent.Line line : content.lines()) {
        html.append("<tr><td style=\"padding: 6px 4px; vertical-align: top;\">")
            .append(escape(line.product()));
        String detail = join(line.colour(), width(line));
        if (!detail.isEmpty()) {
          html.append("<br><span style=\"color: #6b7280;\">")
              .append(escape(detail))
              .append("</span>");
        }
        html.append("</td><td style=\"padding: 6px 4px; vertical-align: top;\">")
            .append(escape(amount(line.quantity()) + " " + nullToEmpty(line.unit())))
            .append(tolerance(line))
            .append("</td><td style=\"padding: 6px 4px; vertical-align: top;\">")
            .append(
                escape(
                    line.unitPrice() == null
                        ? "To be priced"
                        : amount(line.unitPrice()) + " " + nullToEmpty(line.currency())))
            .append("</td></tr>");
      }
      html.append("</table>");
    }
    for (OrderVersionContent.Total total : content.totals()) {
      html.append("<p style=\"margin: 8px 0 0 0; text-align: right; font-weight: 600;\">Total ")
          .append(escape(total.currency()))
          .append(": ")
          .append(escape(amount(total.total())))
          .append("</p>");
    }
    if (!content.customRequests().isEmpty()) {
      html.append("<p style=\"margin: 16px 0 4px 0; font-weight: 600;\">Custom requests</p><ul>");
      for (OrderVersionContent.Request request : content.customRequests()) {
        html.append("<li>").append(escape(request.description())).append("</li>");
      }
      html.append("</ul>");
    }
    return html.toString();
  }

  private static void row(StringBuilder html, String label, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    html.append("<tr><td style=\"padding: 4px 8px 4px 0; color: #6b7280; width: 40%;\">")
        .append(label)
        .append("</td><td style=\"padding: 4px 0;\">")
        .append(escape(value))
        .append("</td></tr>");
  }

  private static String tolerance(OrderVersionContent.Line line) {
    if (line.toleranceUpPct() == null && line.toleranceDownPct() == null) {
      return "";
    }
    return "<br><span style=\"color: #6b7280;\">"
        + escape(
            "+" + amount(line.toleranceUpPct()) + "% / -" + amount(line.toleranceDownPct()) + "%")
        + "</span>";
  }

  private static String width(OrderVersionContent.Line line) {
    return line.finishedWidth() == null
        ? null
        : amount(line.finishedWidth()) + " " + nullToEmpty(line.finishedWidthUnit());
  }

  private static String join(String first, String second) {
    if (first == null || first.isBlank()) {
      return second == null ? "" : second.trim();
    }
    return second == null || second.isBlank() ? first : first + ", " + second.trim();
  }

  private static String date(LocalDate value) {
    return value == null ? null : DATE.format(value);
  }

  private static String edition(Enum<?> version) {
    return version == null
        ? ""
        : version.name().replace('_', ' ').replace("INCOTERMS", "Incoterms");
  }

  private static String event(Enum<?> event) {
    if (event == null) {
      return "";
    }
    String text = event.name().replace('_', ' ').toLowerCase(Locale.UK);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private static String amount(BigDecimal value) {
    return value == null ? "0" : value.stripTrailingZeros().toPlainString();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** HTML-escaped, with template braces neutralised so a value never reads as a placeholder. */
  static String escape(String value) {
    return value == null ? "" : HtmlUtils.htmlEscape(value).replace("{", "&#123;");
  }
}
