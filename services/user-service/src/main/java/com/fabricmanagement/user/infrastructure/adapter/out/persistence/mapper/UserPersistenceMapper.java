package com.fabricmanagement.user.infrastructure.adapter.out.persistence.mapper;

import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {

    @Mapping(target = "domainEvents", ignore = true)
    User toDomainModel(UserJpaEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserJpaEntity toJpaEntity(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateJpaEntity(User user, @MappingTarget UserJpaEntity entity);
}