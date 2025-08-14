package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.user.application.dto.query.UserResponse;
import com.fabricmanagement.user.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserApplicationMapper {

    UserApplicationMapper INSTANCE = Mappers.getMapper(UserApplicationMapper.class);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "firstName", target = "firstName")
    @Mapping(source = "lastName", target = "lastName")
    @Mapping(source = "username", target = "username")
    @Mapping(expression = "java(user.getFullName())", target = "fullName")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    UserResponse toResponse(User user);
}