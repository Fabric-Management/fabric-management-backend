package com.fabricmanagement.platform.communication.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.dto.ContactDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ContactMapper {

  ContactDto toDto(Contact entity);
}
