package com.fabricmanagement.company.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Company Service Health Indicator with DataSource check
 */
@Component
public class CompanyServiceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public CompanyServiceHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("service", "company-service")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection invalid")
                    .withDetail("service", "company-service")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .withDetail("service", "company-service")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
