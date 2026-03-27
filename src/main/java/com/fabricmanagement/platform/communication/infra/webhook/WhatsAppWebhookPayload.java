package com.fabricmanagement.platform.communication.infra.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * WhatsApp Business API Webhook Payload.
 *
 * <p>Meta sends webhook notifications for message status updates (sent, delivered, read, failed).
 *
 * <p><b>Webhook Events:</b>
 *
 * <ul>
 *   <li>sent - Message sent to WhatsApp server
 *   <li>delivered - Message delivered to recipient
 *   <li>read - Message read by recipient
 *   <li>failed - Message delivery failed
 * </ul>
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks">WhatsApp
 *     Webhooks</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookPayload {

  @JsonProperty("object")
  private String object;

  @JsonProperty("entry")
  private List<Entry> entry;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Entry {
    @JsonProperty("id")
    private String id;

    @JsonProperty("changes")
    private List<Change> changes;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Change {
    @JsonProperty("value")
    private Value value;

    @JsonProperty("field")
    private String field;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Value {
    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("statuses")
    private List<Status> statuses;

    @JsonProperty("messages")
    private List<Message> messages;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Metadata {
    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;

    @JsonProperty("phone_number_id")
    private String phoneNumberId;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Status {
    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("recipient_id")
    private String recipientId;

    @JsonProperty("conversation")
    private Conversation conversation;

    @JsonProperty("pricing")
    private Pricing pricing;

    @JsonProperty("errors")
    private List<Error> errors;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Conversation {
    @JsonProperty("id")
    private String id;

    @JsonProperty("origin")
    private Origin origin;

    @JsonProperty("expiration_timestamp")
    private String expirationTimestamp;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Origin {
    @JsonProperty("type")
    private String type;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Pricing {
    @JsonProperty("billable")
    private Boolean billable;

    @JsonProperty("pricing_model")
    private String pricingModel;

    @JsonProperty("category")
    private String category;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Error {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("title")
    private String title;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error_data")
    private ErrorData errorData;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ErrorData {
    @JsonProperty("details")
    private String details;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Message {
    @JsonProperty("from")
    private String from;

    @JsonProperty("id")
    private String id;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("type")
    private String type;

    @JsonProperty("text")
    private Text text;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Text {
    @JsonProperty("body")
    private String body;
  }
}
