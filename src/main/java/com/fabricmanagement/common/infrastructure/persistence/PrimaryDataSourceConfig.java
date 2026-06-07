package com.fabricmanagement.common.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Primary DataSource and TransactionManager configuration.
 *
 * <p>The explicit {@code transactionManager} bean is required because Spring Boot's {@code
 * JpaBaseConfiguration} has {@code @ConditionalOnMissingBean(TransactionManager.class)}. Since
 * {@link SystemDataSourceConfig} defines a second {@code PlatformTransactionManager} ({@code
 * systemTransactionManager}), the auto-config condition is satisfied and Spring Boot does NOT
 * create the JPA transaction manager. Without this bean, any {@code @Transactional} method fails
 * with: "No bean named 'transactionManager' available".
 */
@Configuration
public class PrimaryDataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource")
  public DataSourceProperties dataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource dataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  /**
   * Explicit JPA transaction manager — required when multiple {@link PlatformTransactionManager}
   * beans exist (e.g., {@code systemTransactionManager} from {@link SystemDataSourceConfig}).
   */
  @Bean
  @Primary
  public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new JpaTransactionManager(emf);
  }
}
