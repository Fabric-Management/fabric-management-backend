package com.fabricmanagement.contact.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Contact domain entity representing a contact in the system.
 * This is a pure domain object without framework dependencies.
 */
public class Contact {
	private UUID id;
	private UUID tenantId;
	private String contactType;
	private String status;
	private String firstName;
	private String lastName;
	private String displayName;
	private String notes;
	private boolean deleted;

	// Constructor
	public Contact() {}

	public Contact(UUID id, UUID tenantId, String contactType, String status,
	               String firstName, String lastName, String displayName, String notes) {
		this.id = id;
		this.tenantId = tenantId;
		this.contactType = contactType;
		this.status = status;
		this.firstName = firstName;
		this.lastName = lastName;
		this.displayName = displayName;
		this.notes = notes;
		this.deleted = false;
	}

	// Domain behavior methods
	public void markAsDeleted() {
		this.deleted = true;
		this.status = "INACTIVE";
	}

	public void activate() {
		this.status = "ACTIVE";
	}

	public void deactivate() {
		this.status = "INACTIVE";
	}

	public String getFullName() {
		if (firstName != null && lastName != null) {
			return firstName + " " + lastName;
		}
		return displayName != null ? displayName : "";
	}

	// Getters and Setters
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	public String getContactType() {
		return contactType;
	}

	public void setContactType(String contactType) {
		this.contactType = contactType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Contact contact = (Contact) o;
		return Objects.equals(id, contact.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "Contact{" +
			"id=" + id +
			", tenantId=" + tenantId +
			", contactType='" + contactType + '\'' +
			", status='" + status + '\'' +
			", displayName='" + displayName + '\'' +
			'}';
	}
}
