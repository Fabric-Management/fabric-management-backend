package com.fabricmanagement.platform.tenant.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.TenantSettingsDto;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface TenantSettingsMapper {

  TenantSettingsDto toDto(TenantSettings entity);

  TenantSettings toEntity(UpdateTenantSettingsRequest request);
}
