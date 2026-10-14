package com.fabricmanagement.platform.tenant.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.TenantSettingsDto;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface TenantSettingsMapper {

  TenantSettingsDto toDto(TenantSettings entity);

  /** The stock-proposal group has its own endpoint; the service keeps the stored one. */
  @Mapping(target = "stockProposal", ignore = true)
  TenantSettings toEntity(UpdateTenantSettingsRequest request);
}
