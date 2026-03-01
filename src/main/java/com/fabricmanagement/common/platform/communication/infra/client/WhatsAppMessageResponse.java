package com.fabricmanagement.common.platform.communication.infra.client;

import java.util.List;
import lombok.Data;

@Data
public class WhatsAppMessageResponse {
  private String messagingProduct;
  private List<Contact> contacts;
  private List<Message> messages;

  @Data
  public static class Contact {
    private String input;
    private String waId;
  }

  @Data
  public static class Message {
    private String id;
  }
}
