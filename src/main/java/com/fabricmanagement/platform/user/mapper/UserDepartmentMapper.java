package com.fabricmanagement.platform.user.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.dto.UserDepartmentDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserDepartmentMapper {

  UserDepartmentDto toDto(UserDepartment entity);

  List<UserDepartmentDto> toDtoList(List<UserDepartment> entities);
}
