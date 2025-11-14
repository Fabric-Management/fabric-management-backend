package com.fabricmanagement.common.platform.communication.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for AddressContact junction entity.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AddressContactId implements Serializable {

    private UUID addressId;
    private UUID contactId;
}

