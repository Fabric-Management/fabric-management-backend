package com.fabricmanagement.platform.organization.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface OrganizationAddressMapper {

  @Mapping(source = "organization.id", target = "organizationId")
  @Mapping(source = "address", target = "address")
  OrganizationAddressDto toDto(OrganizationAddress entity);

  AddressDto addressToDto(Address address);
}
