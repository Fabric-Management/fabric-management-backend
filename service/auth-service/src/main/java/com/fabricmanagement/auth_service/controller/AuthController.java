package com.fabricmanagement.auth_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @GetMapping("/health")
    public String health() {
        return "Auth Service is running!";
    }
    
    @GetMapping("/login")
    public String login() {
        return "Login endpoint - TODO: Implement Keycloak integration";
    }
}