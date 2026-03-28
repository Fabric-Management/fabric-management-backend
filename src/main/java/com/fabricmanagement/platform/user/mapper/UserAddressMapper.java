package com.fabricmanagement.platform.user.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.user.domain.UserAddress;
import com.fabricmanagement.platform.user.dto.UserAddressDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserAddressMapper {

  UserAddressDto toDto(UserAddress entity);

  List<UserAddressDto> toDtoList(List<UserAddress> entities);

  AddressDto addressToDto(Address address);
}
