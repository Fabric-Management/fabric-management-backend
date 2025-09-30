package com.fabricmanagement.user.domain.valueobject;

import lombok.Value;

/**
 * User Contact Information Value Object
 * 
 * Represents a verified contact method for user authentication
 */
@Value
public class UserContact {
    String contactValue;        // email or phone number
    ContactType contactType;    // EMAIL or PHONE
    boolean isVerified;         // verification status
    boolean isPrimary;          // primary contact for login
    
    public enum ContactType {
        EMAIL,
        PHONE
    }
    
    public static UserContact email(String email, boolean isVerified, boolean isPrimary) {
        return new UserContact(email, ContactType.EMAIL, isVerified, isPrimary);
    }
    
    public static UserContact phone(String phone, boolean isVerified, boolean isPrimary) {
        return new UserContact(phone, ContactType.PHONE, isVerified, isPrimary);
    }
}
