package com.fabricmanagement.fiber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Fiber Service Application
 *
 * Foundation service for textile chain - manages all natural, synthetic,
 * artificial, and mineral fiber definitions.
 *
 * Service Characteristics:
 * - GLOBAL service (tenant-independent fiber registry)
 * - Event-Driven (Choreography pattern)
 * - Redis caching (1h TTL)
 * - Foundation for yarn, weaving, finishing services
 *
 * Port: 8094
 * Base Path: /api/v1/fibers
 *
 * @see <a href="../../../../../../docs/services/fabric-fiber-service/fabric-fiber-service.md">Service Documentation</a>
 */
@SpringBootApplication(scanBasePackages = {
        "com.fabricmanagement.fiber",
        "com.fabricmanagement.shared"
})
@EnableJpaAuditing
@EnableCaching
@EnableKafka
@EnableFeignClients
public class FiberServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiberServiceApplication.class, args);
    }
}

