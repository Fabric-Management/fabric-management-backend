package com.fabricmanagement.company.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Company Event Store
 * 
 * Stores domain events in the database for event sourcing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventStore {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Stores a domain event
     */
    public void storeEvent(UUID companyId, Object event) {
        try {
            String eventType = event.getClass().getSimpleName();
            String eventData = objectMapper.writeValueAsString(event);
            
            String sql = """
                INSERT INTO company_events (id, company_id, event_type, event_data, event_version, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, NOW())
                """;
            
            jdbcTemplate.update(sql, 
                UUID.randomUUID(),
                companyId,
                eventType,
                eventData,
                1
            );
            
            log.debug("Event stored: {} for company: {}", eventType, companyId);
            
        } catch (Exception e) {
            log.error("Failed to store event for company: {}", companyId, e);
        }
    }
    
    /**
     * Retrieves all events for a company
     */
    public void getEventsForCompany(UUID companyId) {
        String sql = """
            SELECT event_type, event_data, created_at 
            FROM company_events 
            WHERE company_id = ? 
            ORDER BY created_at ASC
            """;
        
        jdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                log.debug("Event: {} at {}", rs.getString("event_type"), rs.getTimestamp("created_at"));
                return null;
            },
            companyId
        );
    }
}

