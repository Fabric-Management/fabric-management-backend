package com.fabricmanagement.contact.infrastructure.integration.company;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client for communicating with company-service.
 */
@Component
public class CompanyServiceClient {
    
    private final RestTemplate restTemplate;
    private final String companyServiceUrl;
    
    public CompanyServiceClient(RestTemplate restTemplate, 
                               @Value("${services.company-service.url}") String companyServiceUrl) {
        this.restTemplate = restTemplate;
        this.companyServiceUrl = companyServiceUrl;
    }
    
    public CompanyDto getCompanyById(UUID companyId) {
        try {
            String url = companyServiceUrl + "/api/companies/" + companyId;
            return restTemplate.getForObject(url, CompanyDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch company: " + e.getMessage(), e);
        }
    }
    
    public boolean existsById(UUID companyId) {
        try {
            String url = companyServiceUrl + "/api/companies/" + companyId + "/exists";
            return restTemplate.getForObject(url, Boolean.class);
        } catch (Exception e) {
            return false;
        }
    }
}
