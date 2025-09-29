package com.fabricmanagement.contact.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Address value object.
 * Represents a physical address with its properties.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ContactAddress {

    private UUID id;
    private String street;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private UserContactInfo.AddressType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}