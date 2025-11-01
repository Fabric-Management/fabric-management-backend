package com.fabricmanagement.common.platform.communication.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for CompanyAddress junction entity.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CompanyAddressId implements Serializable {

    private UUID companyId;
    private UUID addressId;
}

