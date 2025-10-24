package com.fabricmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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

    public static void main(String[] args) {
        SpringApplication.run(FabricManagementApplication.class, args);
    }
}

