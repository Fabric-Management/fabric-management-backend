package com.fabricmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Fabric Management System - Modular Monolith Application
 *
 * <p>A comprehensive multi-tenant fabric management system built with
 * Domain-Driven Design (DDD) and Modular Monolith architecture.</p>
 *
 * <h2>Architecture Principles:</h2>
 * <ul>
 *   <li><b>Modular Monolith:</b> Clean domain boundaries, in-process communication</li>
 *   <li><b>Multi-Tenant:</b> Row-Level Security (RLS) with tenant_id isolation</li>
 *   <li><b>Event-Driven:</b> Domain events for loose coupling between modules</li>
 *   <li><b>Policy-Controlled:</b> 5-layer policy engine for authorization</li>
 *   <li><b>Self-Healing:</b> Degraded mode support (Kafka, Redis optional)</li>
 * </ul>
 *
 * <h2>Module Structure:</h2>
 * <pre>
 * common/
 * ├─ platform/         → auth, user, company, policy, audit, communication
 * ├─ infrastructure/   → persistence, events, web, mapping, cqrs, security
 * └─ util/             → Money, Unit, TimeHelper
 *
 * Business Modules:
 * ├─ production/       → masterdata, planning, execution, quality
 * ├─ logistics/        → inventory, shipment, customs
 * ├─ finance/          → ar, ap, invoice, costing
 * ├─ human/            → employee, org, leave, payroll, performance
 * ├─ procurement/      → supplier, requisition, rfq, po, grn
 * ├─ integration/      → adapters, webhooks, transforms, notifications
 * └─ insight/          → analytics, intelligence
 * </pre>
 *
 * @version 1.0.0
 * @since 2025-01-27
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class FabricManagementApplication {

    /**
     * RestTemplate bean with optimized timeout settings for frontend API calls.
     * 
     * <p>Performance optimizations:</p>
     * <ul>
     *   <li>Connection timeout: 2 seconds (fail fast if frontend unreachable)</li>
     *   <li>Read timeout: 5 seconds (template rendering should be fast)</li>
     *   <li>No write timeout needed (we're sending small JSON payloads)</li>
     * </ul>
     * 
     * <p>This prevents email sending from blocking if frontend is slow or down,
     * allowing fast fallback to backend templates.</p>
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2 seconds - fail fast if unreachable
        factory.setReadTimeout(5000);    // 5 seconds - template rendering timeout
        
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(FabricManagementApplication.class, args);
    }
}

