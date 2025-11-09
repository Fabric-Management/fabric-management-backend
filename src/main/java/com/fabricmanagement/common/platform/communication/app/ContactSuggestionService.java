package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.CompanyContact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.dto.ContactSuggestionsDto;
import com.fabricmanagement.common.platform.communication.dto.PhoneSuggestion;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contact Suggestion Service - Smart defaults for user contact creation.
 *
 * <p>Generates intelligent contact suggestions based on company contacts and user name.</p>
 * <p><b>Purpose:</b> Minimize manual data entry, maximize automation.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>✅ Phone suggestion from company default phone</li>
 *   <li>✅ Email suggestions from company domain (multiple formats)</li>
 *   <li>✅ User-friendly labels and sources</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * ContactSuggestionsDto suggestions = contactSuggestionService.getSuggestions(
 *     companyId, "John", "Smith"
 * );
 * // Returns: phone suggestion + email suggestions
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactSuggestionService {

    private final CompanyContactService companyContactService;
    private final CompanyRepository companyRepository;

    /**
     * Generate contact suggestions from company.
     *
     * <p>Returns suggestions (not auto-created) for user approval.</p>
     * <p><b>Smart Logic:</b></p>
     * <ul>
     *   <li>Phone: Uses company's default phone if available</li>
     *   <li>Email: Generates multiple formats from company domain</li>
     * </ul>
     *
     * @param companyId Company ID
     * @param firstName User first name
     * @param lastName User last name
     * @return Contact suggestions (phone + emails)
     */
    @Transactional(readOnly = true)
    public ContactSuggestionsDto getSuggestions(UUID companyId, String firstName, String lastName) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting contact suggestions: tenantId={}, companyId={}, firstName={}, lastName={}", 
            tenantId, companyId, firstName, lastName);

        // Validate company exists and belongs to tenant
        companyRepository.findByTenantIdAndId(tenantId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        List<CompanyContact> companyContacts = companyContactService.getCompanyContacts(companyId);

        ContactSuggestionsDto suggestions = ContactSuggestionsDto.builder()
            .phoneSuggestion(extractPhoneSuggestion(companyContacts))
            .emailSuggestions(generateEmailSuggestions(firstName, lastName, companyContacts))
            .build();

        log.debug("Generated suggestions: phone={}, emailCount={}", 
            suggestions.getPhoneSuggestion() != null ? "available" : "none",
            suggestions.getEmailSuggestions() != null ? suggestions.getEmailSuggestions().size() : 0);

        return suggestions;
    }

    /**
     * Extract phone suggestion from company contacts.
     *
     * <p>Uses company's default phone contact if available.</p>
     *
     * @param companyContacts List of company contacts
     * @return Phone suggestion or null if not available
     */
    private PhoneSuggestion extractPhoneSuggestion(List<CompanyContact> companyContacts) {
        return companyContacts.stream()
            .filter(cc -> cc.getContact() != null)
            .filter(cc -> cc.getContact().getContactType() != null && cc.getContact().getContactType().isPhone())
            .filter(cc -> Boolean.TRUE.equals(cc.getIsDefault()))
            .findFirst()
            .map(cc -> PhoneSuggestion.builder()
                .value(cc.getContact().getContactValue())
                .source("company")
                .label("Use company phone?")
                .build())
            .orElse(null);
    }

    /**
     * Generate email suggestions from company domain.
     *
     * <p>Generates multiple common email formats:</p>
     * <ul>
     *   <li>firstname.lastname@company.com (most common)</li>
     *   <li>firstname@company.com</li>
     *   <li>firstinitial+lastname@company.com</li>
     *   <li>firstname+lastinitial@company.com</li>
     * </ul>
     *
     * @param firstName User first name
     * @param lastName User last name
     * @param companyContacts List of company contacts (to extract domain)
     * @return List of email suggestions (ordered by commonality)
     */
    private List<String> generateEmailSuggestions(String firstName, String lastName,
                                                  List<CompanyContact> companyContacts) {
        if (firstName == null || firstName.isBlank() || 
            lastName == null || lastName.isBlank()) {
            log.debug("Missing name information for email suggestions");
            return Collections.emptyList();
        }

        Optional<String> companyDomain = companyContacts.stream()
            .filter(cc -> cc.getContact() != null)
            .filter(cc -> cc.getContact().getContactType() == ContactType.EMAIL)
            .findFirst()
            .map(cc -> extractDomain(cc.getContact().getContactValue()));

        if (companyDomain.isEmpty()) {
            log.debug("No company email found, cannot generate suggestions");
            return Collections.emptyList();
        }

        String domain = companyDomain.get();
        String firstNameLower = firstName.toLowerCase().trim();
        String lastNameLower = lastName.toLowerCase().trim();

        // Validate names are not empty after trimming
        if (firstNameLower.isEmpty() || lastNameLower.isEmpty()) {
            log.debug("Invalid name after normalization");
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();

        // Format 1: firstname.lastname@domain (most common, highest priority)
        suggestions.add(String.format("%s.%s@%s", firstNameLower, lastNameLower, domain));

        // Format 2: firstname@domain (simple, common)
        suggestions.add(String.format("%s@%s", firstNameLower, domain));

        // Format 3: firstinitial+lastname@domain (e.g., jsmith@company.com)
        if (firstNameLower.length() > 0 && lastNameLower.length() > 0) {
            suggestions.add(String.format("%s%s@%s", 
                firstNameLower.charAt(0), lastNameLower, domain));
        }

        // Format 4: firstname+lastinitial@domain (e.g., johns@company.com)
        if (firstNameLower.length() > 0 && lastNameLower.length() > 0) {
            suggestions.add(String.format("%s%s@%s", 
                firstNameLower, lastNameLower.charAt(0), domain));
        }

        log.debug("Generated {} email suggestions for domain: {}", suggestions.size(), domain);
        return suggestions;
    }

    /**
     * Extract domain from email address.
     *
     * @param email Email address
     * @return Domain part (e.g., "company.com")
     */
    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf('@') + 1);
    }
}

