package com.fabricmanagement.company.application.dto.company.response;

import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO containing detailed company information.
 */
public record CompanyDetailResponse(
    String id,
    String name,
    String description,
    CompanyType companyType,
    Industry industry,
    String website,
    String taxId,
    String registrationNumber,
    LocalDateTime foundedDate,
    String status,
    AddressResponse headquarters,
    List<EmailResponse> emails,
    List<PhoneResponse> phones,
    List<AddressResponse> addresses,
    ContactPersonResponse primaryContact,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy
) {

    /**
     * Nested record for address information.
     */
    public record AddressResponse(
        String street,
        String city,
        String state,
        String postalCode,
        String country,
        String type
    ) {}

    /**
     * Nested record for email information.
     */
    public record EmailResponse(
        String email,
        String type,
        boolean isPrimary
    ) {}

    /**
     * Nested record for phone information.
     */
    public record PhoneResponse(
        String number,
        String type,
        boolean isPrimary
    ) {}

    /**
     * Nested record for contact person information.
     */
    public record ContactPersonResponse(
        String firstName,
        String lastName,
        String email,
        String phone,
        String position
    ) {}
}
