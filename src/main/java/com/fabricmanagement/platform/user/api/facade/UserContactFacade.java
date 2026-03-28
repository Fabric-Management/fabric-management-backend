package com.fabricmanagement.platform.user.api.facade;

import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.dto.CreateContactRequest;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.dto.UserContactDto;
import com.fabricmanagement.platform.user.mapper.UserContactMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserContactFacade {

  private final UserContactAssignmentService userContactAssignmentService;
  private final ContactService contactService;
  private final UserContactMapper userContactMapper;

  @Transactional(readOnly = true)
  public List<UserContactDto> getUserContacts(UUID userId) {
    return userContactMapper.toDtoList(userContactAssignmentService.getUserContacts(userId));
  }

  @Transactional(readOnly = true)
  public Optional<UserContactDto> getDefaultContact(UUID userId) {
    return userContactAssignmentService.getDefaultContact(userId).map(userContactMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Optional<UserContactDto> getAuthenticationContact(UUID userId) {
    return userContactAssignmentService
        .getAuthenticationContact(userId)
        .map(userContactMapper::toDto);
  }

  @Transactional
  public UserContactDto assignContact(UUID userId, UUID contactId, Boolean isDefault) {
    return userContactMapper.toDto(
        userContactAssignmentService.assignContact(userId, contactId, isDefault));
  }

  @Transactional
  public UserContactDto createAndAssignContact(
      UUID userId, CreateContactRequest request, Boolean isDefault) {
    Contact contact =
        contactService.createContact(
            request.getContactValue(),
            request.getContactType(),
            request.getLabel(),
            request.getIsPersonal(),
            request.getParentContactId());
    return assignContact(userId, contact.getId(), isDefault);
  }

  @Transactional
  public UserContactDto setAsDefault(UUID userId, UUID contactId) {
    return userContactMapper.toDto(userContactAssignmentService.setAsDefault(userId, contactId));
  }

  @Transactional
  public void removeContact(UUID userId, UUID contactId) {
    userContactAssignmentService.removeContact(userId, contactId);
  }
}
