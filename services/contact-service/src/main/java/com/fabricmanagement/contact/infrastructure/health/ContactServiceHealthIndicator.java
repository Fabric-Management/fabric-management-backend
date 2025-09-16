package com.fabricmanagement.contact.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Contact Service Health Indicator with DataSource check
 */
@Component
public class ContactServiceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public ContactServiceHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("service", "contact-service")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection invalid")
                    .withDetail("service", "contact-service")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .withDetail("service", "contact-service")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}