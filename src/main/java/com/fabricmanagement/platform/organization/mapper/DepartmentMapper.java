package com.fabricmanagement.platform.organization.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.dto.DepartmentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface DepartmentMapper {

  @Mapping(source = "parentDepartment.id", target = "parentDepartmentId")
  @Mapping(source = "parentDepartment.departmentName", target = "parentDepartmentName")
  DepartmentDto toDto(Department entity);
}
