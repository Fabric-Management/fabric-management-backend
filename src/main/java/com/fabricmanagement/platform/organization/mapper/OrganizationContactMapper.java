package com.fabricmanagement.platform.organization.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.dto.ContactDto;
import com.fabricmanagement.platform.organization.domain.OrganizationContact;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface OrganizationContactMapper {

  @Mapping(source = "organization.id", target = "organizationId")
  @Mapping(source = "contact", target = "contact")
  OrganizationContactDto toDto(OrganizationContact entity);

  ContactDto contactToDto(Contact contact);
}
