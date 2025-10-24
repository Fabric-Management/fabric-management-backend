package com.fabricmanagement.common.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class HealthController {

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @Value("${info.app.name:Fabric Management System}")
    private String appName;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check endpoint called");
        
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", appName,
            "architecture", "Modular Monolith",
            "timestamp", Instant.now(),
            "version", appVersion
        ));
    }
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        log.debug("Info endpoint called");
        
        return ResponseEntity.ok(Map.of(
            "application", appName,
            "description", "Multi-tenant Fabric Management Platform",
            "architecture", "Modular Monolith",
            "version", appVersion,
            "java", System.getProperty("java.version"),
            "modules", buildModulesInfo()
        ));
    }
    
    private Map<String, String> buildModulesInfo() {
        return Map.of(
            "common", "Platform & Infrastructure",
            "production", "Material, Planning, Execution, Quality",
            "logistics", "Inventory, Shipment, Customs",
            "finance", "AR, AP, Invoice, Costing",
            "human", "Employee, Org, Leave, Payroll",
            "procurement", "Supplier, Requisition, RFQ, PO",
            "integration", "Adapters, Webhooks",
            "insight", "Analytics, Intelligence"
        );
    }
}

