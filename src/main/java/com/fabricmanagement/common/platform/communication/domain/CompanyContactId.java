package com.fabricmanagement.common.platform.communication.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for CompanyContact junction entity.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CompanyContactId implements Serializable {

    private UUID companyId;
    private UUID contactId;
}

