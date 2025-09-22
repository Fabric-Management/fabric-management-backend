package com.fabricmanagement.contact.domain.repository;

import com.fabricmanagement.contact.domain.model.Contact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Contact domain entities.
 * This is a domain interface - implementations should handle persistence details.
 */
public interface ContactRepository {
	Contact save(Contact contact);
	Optional<Contact> findById(UUID id);
	List<Contact> findAll();
	void deleteById(UUID id);
}
