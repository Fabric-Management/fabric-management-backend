package com.fabricmanagement.common.platform.communication.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for UserContact junction entity.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserContactId implements Serializable {

    private UUID userId;
    private UUID contactId;
}

