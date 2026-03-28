package com.fabricmanagement.platform.user.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.dto.ContactDto;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.dto.UserContactDto;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface UserContactMapper {

  UserContactDto toDto(UserContact entity);

  List<UserContactDto> toDtoList(List<UserContact> entities);

  ContactDto contactToDto(Contact contact);
}
