package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contact data for user creation (email or phone).
 *
 * <p>Used in CreateInternalUserRequest and CreateExternalUserRequest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactData {
  @NotBlank(message = "Contact value is required")
  @Size(max = 255, message = "Contact value must be at most 255 characters")
  private String contactValue;

  @NotNull(message = "Contact type is required")
  private ContactType contactType;

  /** Label for this contact (e.g., "Work Email", "Personal Phone", "Mobile") */
  private String label;

  /** Whether this is a personal contact (true) or work contact (false). Default: true (personal) */
  @Builder.Default private Boolean isPersonal = true;

  /**
   * Whether this contact has WhatsApp capability (for PHONE contacts only). If null, system will
   * check automatically via WhatsApp API. If true, verification codes and notifications will
   * prioritize WhatsApp. Note: Only applicable for MOBILE phones, not LANDLINE.
   */
  private Boolean isWhatsApp;

  public static final String PHONE_TYPE_MOBILE = "MOBILE";
  public static final String PHONE_TYPE_LANDLINE = "LANDLINE";

  /**
   * Phone type (for PHONE contacts only). If null, defaults to MOBILE for backward compatibility.
   * Allowed values: "MOBILE", "LANDLINE"
   *
   * <p>WhatsApp capability is only checked for MOBILE phones. LANDLINE phones cannot have WhatsApp.
   */
  private String phoneType; // "MOBILE" or "LANDLINE"
}
