package com.fabricmanagement.platform.auth.domain;

import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.util.List;

/**
 * The company and person details of a self-service signup form, kept in {@link PendingSignup} until
 * the e-mailed link is used. The e-mail address, tax number and terms-accepted time have their own
 * columns, so they are not repeated here.
 *
 * <p>The intent is stored by its enum name ({@code SignupIntent}); the code that completes the
 * signup reads it back, treating an unknown value as the playground.
 */
public record PendingSignupRequest(
    String organizationName,
    OrganizationType organizationType,
    String firstName,
    String lastName,
    List<String> selectedOS,
    String intent) {}
