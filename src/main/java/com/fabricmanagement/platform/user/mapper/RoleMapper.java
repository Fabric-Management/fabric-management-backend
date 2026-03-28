package com.fabricmanagement.platform.user.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.dto.RoleDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface RoleMapper {

  RoleDto toDto(Role entity);

  List<RoleDto> toDtoList(List<Role> entities);
}
