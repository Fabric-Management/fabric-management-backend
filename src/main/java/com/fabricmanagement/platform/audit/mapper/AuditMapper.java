package com.fabricmanagement.platform.audit.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.audit.domain.AuditLog;
import com.fabricmanagement.platform.audit.dto.AuditLogDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface AuditMapper {

  AuditLogDto toDto(AuditLog entity);
}
