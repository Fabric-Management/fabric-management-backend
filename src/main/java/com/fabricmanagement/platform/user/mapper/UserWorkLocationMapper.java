package com.fabricmanagement.platform.user.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.user.domain.UserWorkLocation;
import com.fabricmanagement.platform.user.dto.UserWorkLocationDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface UserWorkLocationMapper {

  @Mapping(source = "organizationAddress.organizationId", target = "organizationId")
  @Mapping(source = "organizationAddress.organization.name", target = "organizationName")
  @Mapping(source = "organizationAddress.address", target = "address")
  UserWorkLocationDto toDto(UserWorkLocation entity);

  List<UserWorkLocationDto> toDtoList(List<UserWorkLocation> entities);

  AddressDto addressToDto(Address address);
}
