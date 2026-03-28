package com.fabricmanagement.platform.policy.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.policy.domain.Policy;
import com.fabricmanagement.platform.policy.dto.PolicyDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface PolicyMapper {

  PolicyDto toDto(Policy entity);
}
