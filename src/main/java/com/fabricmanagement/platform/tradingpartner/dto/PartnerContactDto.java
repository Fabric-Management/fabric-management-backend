package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import java.time.Instant;
import java.util.UUID;

public record PartnerContactDto(
    UUID id,
    UUID partnerId,
    String name,
    String email,
    String phone,
    boolean whatsappEnabled,
    PartnerContactRole role,
    boolean primary,
    Boolean isActive,
    Instant createdAt) {

  public static PartnerContactDto from(PartnerContact contact) {
    return new PartnerContactDto(
        contact.getId(),
        contact.getPartner().getId(),
        contact.getName(),
        contact.getEmail(),
        contact.getPhone(),
        contact.isWhatsappEnabled(),
        contact.getRole(),
        contact.isPrimary(),
        contact.getIsActive(),
        contact.getCreatedAt());
  }
}
