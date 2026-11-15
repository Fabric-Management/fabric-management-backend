package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CommonDomainException;
import com.fabricmanagement.common.util.PhoneValidationUtil;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.domain.event.ContactPointChangedEvent;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contact Service - Business logic for contact management.
 *
 * <p>Handles CRUD operations for Contact entities with verification support.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Contact creation and validation
 *   <li>Contact verification status management
 *   <li>Primary contact designation
 *   <li>Extension phone management
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[0-9]{1,10}$");

  private final ContactRepository contactRepository;
  private final DomainEventPublisher eventPublisher;

  public record OnboardingContact(String value, ContactType type) {}

  @Transactional
  public Contact reuseOrCreateOnboardingContact(OnboardingContact prepared) {
    if (prepared.type() == ContactType.EMAIL) {
      var existing =
          contactRepository
              .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
                  TenantContext.requireTenantId(), ContactType.EMAIL, prepared.value())
              .stream()
              .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
              .findFirst();
      if (existing.isPresent()) return existing.get();
    }
    return createContact(prepared.value(), prepared.type(), null);
  }

  /** Validate without writes; telephone type comes from numbering metadata, not E.164 alone. */
  public OnboardingContact prepareOnboardingContact(String value, ContactType requestedType) {
    if (value == null) return null;
    String normalised =
        normalizeContactValue(
            value, requestedType == ContactType.EMAIL ? ContactType.EMAIL : ContactType.LANDLINE);
    ContactType type = requestedType;
    if (type == ContactType.EMAIL) {
      normalised = normalised.toLowerCase(Locale.ROOT);
    } else if (type == null) {
      var phones = PhoneNumberUtil.getInstance();
      try {
        var parsed = phones.parse(normalised, "ZZ");
        type =
            switch (phones.getNumberType(parsed)) {
              case MOBILE -> ContactType.MOBILE;
              case FIXED_LINE, FIXED_LINE_OR_MOBILE -> ContactType.LANDLINE;
              default ->
                  throw new PlatformDomainException(
                      "Company telephone requires a supported international number",
                      "ONBOARDING_PHONE_INVALID",
                      422);
            };
        normalised = phones.format(parsed, PhoneNumberFormat.E164);
      } catch (NumberParseException failure) {
        throw new PlatformDomainException(
            "Invalid company telephone", "ONBOARDING_PHONE_INVALID", 422);
      }
    }
    validateContactValue(normalised, type);
    return new OnboardingContact(normalised, type);
  }

  @Transactional
  public Contact createContact(String contactValue, ContactType contactType, UUID parentContactId) {
    UUID tenantId = TenantContext.requireTenantId();
    ContactType resolvedType = contactType != null ? contactType : inferContactType(contactValue);
    String normalizedValue = normalizeContactValue(contactValue, resolvedType);
    log.debug(
        "Creating contact: tenantId={}, type={}, value={}",
        tenantId,
        resolvedType,
        maskContactValue(normalizedValue));

    validateContactValue(normalizedValue, resolvedType);

    if (resolvedType == ContactType.PHONE_EXTENSION && parentContactId == null) {
      throw new CommonDomainException("PHONE_EXTENSION requires parentContactId");
    }

    if (resolvedType == ContactType.PHONE_EXTENSION) {
      Contact parent =
          contactRepository
              .findById(parentContactId)
              .orElseThrow(() -> new CommonDomainException("Parent contact not found"));

      if (!parent.getContactType().isLandline()) {
        throw new CommonDomainException("Parent contact must be of type LANDLINE");
      }

      if (!parent.getTenantId().equals(tenantId)) {
        throw new CommonDomainException("Parent contact must belong to same tenant");
      }
    }

    Optional<Contact> existing = findPoint(tenantId, normalizedValue, resolvedType);
    if (existing.isPresent()) {
      Contact existingContact = existing.get();
      if (!Boolean.TRUE.equals(existingContact.getIsActive())) {
        existingContact.activate();
        existingContact.setIsVerified(false);
        applyWhatsAppRules(existingContact);
      }
      return existingContact;
    }

    Contact contact =
        Contact.builder()
            .contactValue(normalizedValue)
            .contactType(resolvedType)
            .parentContactId(parentContactId)
            .isVerified(false)
            .build();

    applyWhatsAppRules(contact);

    return contactRepository.save(contact);
  }

  @Transactional(readOnly = true)
  public Optional<Contact> findById(UUID contactId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding contact: tenantId={}, contactId={}", tenantId, contactId);

    return contactRepository.findById(contactId).filter(c -> c.getTenantId().equals(tenantId));
  }

  @Transactional(readOnly = true)
  public List<Contact> findByType(ContactType contactType) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding contacts by type: tenantId={}, type={}", tenantId, contactType);

    return contactRepository.findByTenantIdAndContactType(tenantId, contactType);
  }

  private Optional<Contact> findPoint(UUID tenant, String value, ContactType type) {
    if (type == ContactType.EMAIL) {
      var matching =
          contactRepository
              .findByTenantIdAndContactTypeAndContactValueIgnoreCaseOrderByCreatedAtAsc(
                  tenant, type, value);
      var active =
          matching.stream().filter(contact -> Boolean.TRUE.equals(contact.getIsActive())).toList();
      if (active.size() > 1) {
        throw new PlatformDomainException(
            "Multiple contacts use this email address", "CONTACT_EMAIL_AMBIGUOUS", 409);
      }
      return active.isEmpty() ? matching.stream().findFirst() : Optional.of(active.getFirst());
    }
    return contactRepository.findByTenantIdAndContactValueAndContactType(tenant, value, type);
  }

  @Transactional(readOnly = true)
  public Optional<Contact> findByValueAndType(String contactValue, ContactType contactType) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedValue = normalizeContactValue(contactValue, contactType);
    log.trace(
        "Finding contact by value and type: tenantId={}, type={}, value={}",
        tenantId,
        contactType,
        maskContactValue(normalizedValue));

    return findPoint(tenantId, normalizedValue, contactType);
  }

  @Transactional(readOnly = true)
  public Optional<Contact> findByValue(String contactValue) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedValue = contactValue != null ? contactValue.trim() : null;
    log.trace(
        "Finding contact by value: tenantId={}, value={}",
        tenantId,
        maskContactValue(normalizedValue));
    return contactRepository.findByTenantIdAndContactValue(tenantId, normalizedValue);
  }

  /**
   * Search contacts by partial value (case-insensitive). Returns up to 20 results.
   *
   * @param query Partial contact value (email, phone, website, etc.)
   * @return Matching contacts scoped to current tenant
   */
  @Transactional(readOnly = true)
  public List<Contact> searchByValue(String query) {
    UUID tenantId = TenantContext.requireTenantId();
    if (query == null || query.isBlank()) {
      return List.of();
    }
    log.trace(
        "Searching contacts: tenantId={}, query={}", tenantId, maskContactValue(query.trim()));
    return contactRepository.searchByTenantIdAndContactValue(
        tenantId, query.trim(), PageRequest.of(0, 20));
  }

  @Transactional(readOnly = true)
  public List<Contact> findExtensionsByParent(UUID parentContactId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding extensions: tenantId={}, parentContactId={}", tenantId, parentContactId);

    return contactRepository.findExtensionsByParentContactId(tenantId, parentContactId);
  }

  @Transactional
  public Contact verifyContact(UUID contactId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Verifying contact: tenantId={}, contactId={}", tenantId, contactId);

    Contact contact =
        contactRepository
            .findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

    if (!contact.getTenantId().equals(tenantId)) {
      throw new CommonDomainException("Contact does not belong to current tenant");
    }

    String valueBefore = contact.getContactValue();
    ContactType typeBefore = contact.getContactType();
    String normalizedValue =
        normalizeContactValue(contact.getContactValue(), contact.getContactType());
    validateContactValue(normalizedValue, contact.getContactType());
    contact.setContactValue(normalizedValue);
    applyWhatsAppRules(contact);

    // Mark contact as verified
    contact.verify();
    Contact savedContact = contactRepository.save(contact);
    publishIfAddressChanged(savedContact, valueBefore, typeBefore);

    // Note: With user-based authentication, AuthUser is linked to User, not
    // Contact.
    // All verified contacts of a user can login using the user's AuthUser.

    return savedContact;
  }

  @Transactional
  public Contact updateContact(Contact contact) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Updating contact: tenantId={}, contactId={}", tenantId, contact.getId());

    if (!contact.getTenantId().equals(tenantId)) {
      throw new CommonDomainException("Contact does not belong to current tenant");
    }

    return contactRepository.save(contact);
  }

  @Transactional
  public void deleteContact(UUID contactId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Deleting contact: tenantId={}, contactId={}", tenantId, contactId);

    Contact contact =
        contactRepository
            .findById(contactId)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

    if (!contact.getTenantId().equals(tenantId)) {
      throw new CommonDomainException("Contact does not belong to current tenant");
    }

    if (contact.getContactType() == ContactType.LANDLINE) {
      List<Contact> extensions =
          contactRepository.findExtensionsByParentContactId(tenantId, contactId);
      for (Contact ext : extensions) {
        log.info(
            "Cascade soft-deleting PHONE_EXTENSION: extensionId={}, parentId={}",
            ext.getId(),
            contactId);
        ext.delete();
        contactRepository.save(ext);
      }
    }

    contact.delete();
    contactRepository.save(contact);
    eventPublisher.publish(
        new ContactPointChangedEvent(tenantId, contactId, ContactPointChangedEvent.DELETED));
  }

  public record ContactPoint(String value, ContactType type) {}

  /** Validate the complete value/type pair before changing either field. */
  public ContactPoint prepareChange(Contact contact, String value, ContactType type) {
    ContactType nextType = type != null ? type : contact.getContactType();
    String nextValue =
        normalizeContactValue(value != null ? value : contact.getContactValue(), nextType);
    validateContactValue(nextValue, nextType);
    if (nextType != contact.getContactType()
        && (nextType == ContactType.PHONE_EXTENSION
            || contact.getContactType() == ContactType.PHONE_EXTENSION)) {
      throw new PlatformDomainException(
          "Create a new contact to change the extension type",
          "CONTACT_EXTENSION_TYPE_CHANGE",
          409);
    }
    return new ContactPoint(nextValue, nextType);
  }

  public boolean samePoint(Contact contact, ContactPoint point) {
    return contact.getContactType() == point.type()
        && (point.type() == ContactType.EMAIL
            ? contact.getContactValue().equalsIgnoreCase(point.value())
            : contact.getContactValue().equals(point.value()));
  }

  @Transactional
  public Contact replacePoint(Contact contact, ContactPoint point) {
    if (samePoint(contact, point)) return contact;
    String valueBefore = contact.getContactValue();
    ContactType typeBefore = contact.getContactType();
    contact.setContactValue(point.value());
    contact.setContactType(point.type());
    contact.setIsVerified(false);
    applyWhatsAppRules(contact);
    Contact saved = contactRepository.save(contact);
    publishIfAddressChanged(saved, valueBefore, typeBefore);
    return saved;
  }

  /**
   * Tells the modules that rested something on this contact point's earlier address — an approval
   * authority, for one — that it now reaches another address. Published in this transaction, so
   * their synchronous listeners end it in the same commit. A change of letter case in an e-mail
   * address reaches the same mailbox and is not reported.
   */
  private void publishIfAddressChanged(
      Contact contact, String valueBefore, ContactType typeBefore) {
    String valueAfter = contact.getContactValue();
    boolean sameType = typeBefore == contact.getContactType();
    boolean sameValue =
        valueBefore != null
            && valueAfter != null
            && (contact.getContactType() == ContactType.EMAIL
                ? valueBefore.trim().equalsIgnoreCase(valueAfter.trim())
                : valueBefore.trim().equals(valueAfter.trim()));
    if (sameType && sameValue) {
      return;
    }
    eventPublisher.publish(
        new ContactPointChangedEvent(
            contact.getTenantId(), contact.getId(), ContactPointChangedEvent.ADDRESS_CHANGED));
  }

  private String maskContactValue(String contactValue) {
    if (contactValue == null) {
      return null;
    }
    // Simple masking for logging
    if (contactValue.contains("@")) {
      return contactValue.replaceAll("(.).*@.*", "$1***@***");
    }
    if (contactValue.startsWith("+")) {
      return contactValue.substring(0, 4) + "***";
    }
    return contactValue.length() > 4 ? contactValue.substring(0, 2) + "***" : "***";
  }

  private void applyWhatsAppRules(Contact contact) {
    // Contact entity has no isWhatsApp field; WhatsApp handling can be added when
    // needed.
  }

  private void validateContactValue(String contactValue, ContactType contactType) {
    if (contactValue == null || contactValue.isBlank()) {
      throw new CommonDomainException("Contact value cannot be empty");
    }
    if (contactType == null) {
      throw new CommonDomainException("Contact type is required");
    }
    String trimmed = contactValue.trim();
    switch (contactType) {
      case EMAIL -> {
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
          throw new CommonDomainException("Invalid email format");
        }
      }
      case MOBILE -> {
        PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validateMobile(trimmed);
        if (!result.isValid()) {
          throw new CommonDomainException(result.getErrorMessage());
        }
      }
      case LANDLINE, FAX -> {
        PhoneValidationUtil.ValidationResult result = PhoneValidationUtil.validateLandline(trimmed);
        if (!result.isValid()) {
          throw new CommonDomainException(result.getErrorMessage());
        }
      }
      case PHONE_EXTENSION -> {
        if (!EXTENSION_PATTERN.matcher(trimmed).matches()) {
          throw new CommonDomainException("Phone extension must be 1-10 digits");
        }
      }
      default -> {
        // WEBSITE, SOCIAL_MEDIA left for future specialized validation
      }
    }
  }

  private String normalizeContactValue(String contactValue, ContactType contactType) {
    if (contactValue == null) {
      return null;
    }
    String trimmed = contactValue.trim();
    if (contactType != null && contactType.isPhone()) {
      String normalized =
          trimmed.replace(" ", "").replace("-", "").replace("(", "").replace(")", "");
      return normalized;
    }
    if (contactType == ContactType.WEBSITE) {
      return trimmed.toLowerCase();
    }
    return contactType == ContactType.EMAIL ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
  }

  private ContactType inferContactType(String contactValue) {
    if (contactValue == null || contactValue.isBlank()) {
      throw new CommonDomainException("Unable to infer contact type from empty value");
    }
    String trimmed = contactValue.trim();
    if (EMAIL_PATTERN.matcher(trimmed).matches()) {
      return ContactType.EMAIL;
    }
    if (PhoneValidationUtil.validateMobile(trimmed).isValid()) {
      return ContactType.MOBILE;
    }
    if (PhoneValidationUtil.validateLandline(trimmed).isValid()) {
      return ContactType.LANDLINE;
    }
    throw new CommonDomainException("Unable to infer contact type from value: " + trimmed);
  }

  /**
   * Batch load contacts by IDs (avoids N+1 problem).
   *
   * @param contactIds List of contact IDs
   * @return List of contacts (only from current tenant)
   */
  @Transactional(readOnly = true)
  public List<Contact> findAllById(List<UUID> contactIds) {
    if (contactIds == null || contactIds.isEmpty()) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Batch loading contacts: tenantId={}, count={}", tenantId, contactIds.size());
    return contactRepository.findAllById(contactIds).stream()
        .filter(contact -> contact.getTenantId().equals(tenantId))
        .toList();
  }
}
