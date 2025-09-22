package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.application.mapper.ContactMapper;
import com.fabricmanagement.contact.domain.model.Contact;
import com.fabricmanagement.contact.domain.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for managing contacts.
 * Handles all business logic related to contact operations.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ContactApplicationService {
    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;

    public ContactDetailResponse createContact(CreateContactRequest request) {
        log.debug("Creating new contact with request: {}", request);
        Contact contact = contactMapper.toDomain(request);
        Contact saved = contactRepository.save(contact);
        log.info("Created contact with ID: {}", saved.getId());
        return contactMapper.toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public ContactDetailResponse getContactById(UUID contactId) {
        log.debug("Fetching contact with ID: {}", contactId);
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("Contact not found with id: " + contactId));
        return contactMapper.toDetailResponse(contact);
    }

    public ContactDetailResponse updateContact(UUID contactId, UpdateContactRequest request) {
        log.debug("Updating contact with ID: {}", contactId);
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("Contact not found with id: " + contactId));
        contactMapper.updateDomainFromRequest(request, contact);
        Contact updated = contactRepository.save(contact);
        log.info("Updated contact with ID: {}", updated.getId());
        return contactMapper.toDetailResponse(updated);
    }

    public void deleteContact(UUID contactId) {
        log.debug("Deleting contact with ID: {}", contactId);
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NoSuchElementException("Contact not found with id: " + contactId));
        contact.markAsDeleted();
        contactRepository.save(contact);
        log.info("Deleted contact with ID: {}", contactId);
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts() {
        log.debug("Listing all contacts");
        return contactRepository.findAll().stream()
                .map(contactMapper::toResponse)
                .collect(Collectors.toList());
    }
}
