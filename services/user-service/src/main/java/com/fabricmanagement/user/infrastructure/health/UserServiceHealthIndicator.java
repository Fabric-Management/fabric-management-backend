package com.fabricmanagement.user.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * User Service Health Indicator with DataSource check
 */
@Component
public class UserServiceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public UserServiceHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("service", "user-service")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection invalid")
                    .withDetail("service", "user-service")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "Connection failed")
                .withDetail("service", "user-service")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
