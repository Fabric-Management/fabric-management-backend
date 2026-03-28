package com.fabricmanagement.platform.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating external users (partner/supplier/customer users).
 *
 * <p><b>No HR data:</b> Only basic user information for external company relationships.
 *
 * <p><b>Use Case:</b> Creating users for partner companies, suppliers, or customers (no HR records
 * needed).
 *
 * <p><b>Required fields:</b>
 *
 * <ul>
 *   <li>firstName, lastName, organizationId - Basic user info
 *   <li>contactValue, contactType - Primary contact for authentication
 * </ul>
 *
 * <p><b>Optional fields:</b>
 *
 * <ul>
 *   <li>additionalContacts - Multiple emails/phones
 *   <li>addresses - Work and home addresses
 *   <li>department - Department assignment
 * </ul>
 *
 * <p><b>Note:</b> No HR data (title, gender, birth date, etc.) - these are for external business
 * relationships only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExternalUserRequest {

  @NotBlank(message = "First name is required")
  @Size(max = 100, message = "First name must be at most 100 characters")
  private String firstName;

  @NotBlank(message = "Last name is required")
  @Size(max = 100, message = "Last name must be at most 100 characters")
  private String lastName;

  @NotBlank(message = "Contact value is required")
  @Size(max = 255, message = "Contact value must be at most 255 characters")
  private String contactValue;

  @NotNull(message = "Contact type is required")
  private com.fabricmanagement.platform.user.domain.ContactType contactType;

  @NotNull(message = "Organization ID is required")
  private UUID organizationId;

  /**
   * @deprecated Not used in external user creation flow. External users don't have department
   *     assignments. Kept for backward compatibility — will be removed in a future version.
   */
  @Deprecated private String department;

  /**
   * When {@code true}, the {@link com.fabricmanagement.platform.user.domain.event.UserCreatedEvent}
   * is not published after creation. The caller is responsible for publishing its own invitation
   * event (e.g., partner portal users publish {@link
   * com.fabricmanagement.platform.tradingpartner.domain.event.PartnerUserCreatedEvent}).
   */
  @Builder.Default private boolean suppressEmailInvitation = false;

  /** Additional contacts (emails, phones) beyond the primary contact. */
  @Builder.Default @Valid private List<ContactData> additionalContacts = new ArrayList<>();

  /** Addresses for the user (work, home, etc.). */
  @Builder.Default @Valid private List<AddressData> addresses = new ArrayList<>();
}
