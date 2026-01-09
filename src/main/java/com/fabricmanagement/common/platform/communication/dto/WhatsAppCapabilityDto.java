package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for WhatsApp capability check response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCapabilityDto {
  private String phoneNumber;
  private Boolean hasWhatsApp;
  private Boolean canReceiveMessages;
}
