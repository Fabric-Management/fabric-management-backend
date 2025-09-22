package com.fabricmanagement.contact.application.messages;

/**
 * Error and success message keys for contact-service responses.
 * Use with message source or directly in ApiResponse.
 */
public final class ContactMessageKeys {
    private ContactMessageKeys() {}

    // Success messages
    public static final String CONTACT_CREATED = "contact.created.success";
    public static final String CONTACT_UPDATED = "contact.updated.success";
    public static final String CONTACT_DELETED = "contact.deleted.success";
    public static final String EMAIL_VERIFIED = "contact.email.verified.success";
    public static final String PHONE_VERIFIED = "contact.phone.verified.success";

    // Error messages
    public static final String CONTACT_NOT_FOUND = "contact.not.found";
    public static final String EMAIL_DUPLICATE = "contact.email.duplicate";
    public static final String PHONE_DUPLICATE = "contact.phone.duplicate";
    public static final String INVALID_EMAIL = "contact.email.invalid";
    public static final String INVALID_PHONE = "contact.phone.invalid";
    public static final String ADDRESS_NOT_FOUND = "contact.address.not.found";
    public static final String UNAUTHORIZED = "contact.unauthorized";
}
