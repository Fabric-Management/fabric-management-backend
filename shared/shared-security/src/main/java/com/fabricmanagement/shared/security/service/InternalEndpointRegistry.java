package com.fabricmanagement.shared.security.service;

import com.fabricmanagement.shared.security.annotation.InternalEndpoint;
import com.fabricmanagement.shared.security.config.InternalEndpointProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Internal Endpoint Registry
 * 
 * Scans application for @InternalEndpoint annotations and configuration.
 * Provides fast lookup for InternalAuthenticationFilter.
 * 
 * Discovery Process:
 * 1. Scan all @RestController classes
 * 2. Find methods with @InternalEndpoint
 * 3. Extract path from @RequestMapping/@GetMapping/etc.
 * 4. Build fast lookup map
 * 5. Add configuration-based patterns (if any)
 * 
 * Performance:
 * - O(1) lookup time
 * - Built once at startup
 * - Cached in memory
 * 
 * @since 3.2.0 - Internal Endpoint Registry (Oct 13, 2025)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalEndpointRegistry {
    
    private final ApplicationContext applicationContext;
    private final InternalEndpointProperties endpointProperties;
    
    private final Map<String, Set<String>> exactPathRegistry = new HashMap<>();
    private final List<PathPattern> regexPatterns = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        log.info("🔍 Scanning for @InternalEndpoint annotations...");
        
        int annotationCount = scanAnnotatedEndpoints();
        int configCount = loadConfiguredEndpoints();
        
        log.info("✅ Internal Endpoint Registry initialized:");
        log.info("   - Annotation-based: {} endpoints", annotationCount);
        log.info("   - Configuration-based: {} endpoints", configCount);
        log.info("   - Total: {} endpoints", annotationCount + configCount);
    }
    
    /**
     * Scan for @InternalEndpoint annotations
     */
    private int scanAnnotatedEndpoints() {
        int count = 0;
        
        Map<String, Object> controllers = applicationContext.getBeansWithAnnotation(RestController.class);
        
        for (Object controller : controllers.values()) {
            Class<?> controllerClass = controller.getClass();
            
            // Get class-level @RequestMapping (base path)
            String basePath = extractBasePath(controllerClass);
            
            // Scan methods
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(InternalEndpoint.class)) {
                    String path = extractMethodPath(method, basePath);
                    String httpMethod = extractHttpMethod(method);
                    
                    registerEndpoint(path, httpMethod);
                    
                    InternalEndpoint annotation = method.getAnnotation(InternalEndpoint.class);
                    log.debug("   📌 {} {} - {} (Called by: {})",
                            httpMethod, path,
                            annotation.description(),
                            String.join(", ", annotation.calledBy()));
                    
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * Load configuration-based patterns
     */
    private int loadConfiguredEndpoints() {
        int count = 0;
        
        for (InternalEndpointProperties.EndpointPattern pattern : endpointProperties.getPatterns()) {
            String path = pattern.getPath();
            String method = pattern.getMethod();
            
            if (pattern.getType() == InternalEndpointProperties.PatternType.REGEX) {
                regexPatterns.add(new PathPattern(path, method, Pattern.compile(path)));
                log.debug("   📌 REGEX: {} {} - {}", method, path, pattern.getDescription());
            } else if (pattern.getType() == InternalEndpointProperties.PatternType.EXACT) {
                registerEndpoint(path, method);
                log.debug("   📌 EXACT: {} {} - {}", method, path, pattern.getDescription());
            } else {
                // PREFIX - will be checked in isInternalEndpoint
                registerEndpoint(path, method);
                log.debug("   📌 PREFIX: {} {} - {}", method, path, pattern.getDescription());
            }
            
            count++;
        }
        
        return count;
    }
    
    /**
     * Check if path+method is an internal endpoint
     */
    public boolean isInternalEndpoint(String path, String method) {
        // 1. Check exact match
        Set<String> methods = exactPathRegistry.get(path);
        if (methods != null && (methods.contains(method) || methods.contains("*"))) {
            return true;
        }
        
        // 2. Check prefix match
        for (String registeredPath : exactPathRegistry.keySet()) {
            if (path.startsWith(registeredPath)) {
                methods = exactPathRegistry.get(registeredPath);
                if (methods.contains(method) || methods.contains("*")) {
                    return true;
                }
            }
        }
        
        // 3. Check regex patterns
        for (PathPattern pattern : regexPatterns) {
            if (pattern.matches(path, method)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Register an endpoint in the registry
     */
    private void registerEndpoint(String path, String httpMethod) {
        exactPathRegistry.computeIfAbsent(path, k -> new HashSet<>()).add(httpMethod);
    }
    
    /**
     * Extract base path from controller class
     */
    private String extractBasePath(Class<?> controllerClass) {
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            return classMapping.value()[0];
        }
        return "";
    }
    
    /**
     * Extract method path
     */
    private String extractMethodPath(Method method, String basePath) {
        String methodPath = "";
        
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            methodPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            methodPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            methodPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            methodPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            methodPath = mapping.value().length > 0 ? mapping.value()[0] : "";
        }
        
        return basePath + methodPath;
    }
    
    /**
     * Extract HTTP method
     */
    private String extractHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";
        
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            if (mapping.method().length > 0) {
                return mapping.method()[0].name();
            }
        }
        
        return "*"; // All methods
    }
    
    /**
     * Path pattern for regex matching
     */
    private static class PathPattern {
        private final String method;
        private final Pattern pattern;
        
        PathPattern(String originalPath, String method, Pattern pattern) {
            this.method = method != null ? method : "*";
            this.pattern = pattern;
        }
        
        boolean matches(String path, String httpMethod) {
            boolean pathMatches = pattern.matcher(path).matches();
            boolean methodMatches = "*".equals(this.method) || this.method.equals(httpMethod);
            return pathMatches && methodMatches;
        }
    }
}

