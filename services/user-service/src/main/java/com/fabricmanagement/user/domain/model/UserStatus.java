package com.fabricmanagement.user.domain.model;

/**
 * Enumeration representing the various states a user can be in.
 * Separate from BaseEntity's deleted flag for business logic purposes.
 */
public enum UserStatus {
    /**
     * User is active and can perform normal operations.
     */
    ACTIVE,

    /**
     * User is temporarily inactive but not deleted.
     */
    INACTIVE,

    /**
     * User is suspended due to policy violations or administrative action.
     */
    SUSPENDED,

    /**
     * User account is pending activation (e.g., after registration).
     */
    PENDING
}