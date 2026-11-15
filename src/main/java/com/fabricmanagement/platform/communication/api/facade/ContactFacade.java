package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.dto.ContactDto;
import com.fabricmanagement.platform.communication.mapper.ContactMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactFacade {

  private final ContactService contactService;
  private final ContactMapper mapper;

  @Transactional(readOnly = true)
  public ContactDto getContact(UUID id) {
    Contact contact =
        contactService
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Contact not found"));
    return mapper.toDto(contact);
  }

  @Transactional(readOnly = true)
  public List<ContactDto> searchContacts(String query) {
    return contactService.searchByValue(query).stream()
        .map(mapper::toDto)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<ContactDto> getContactsByType(ContactType type) {
    return contactService.findByType(type).stream().map(mapper::toDto).collect(Collectors.toList());
  }
}
