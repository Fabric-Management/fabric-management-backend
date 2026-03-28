package com.fabricmanagement.platform.communication.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface AddressMapper {

  AddressDto toDto(Address entity);
}
