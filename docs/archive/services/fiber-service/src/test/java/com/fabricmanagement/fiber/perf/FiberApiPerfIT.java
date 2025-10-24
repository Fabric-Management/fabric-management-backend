package com.fabricmanagement.fiber.perf;

import com.fabricmanagement.fiber.FiberServiceApplication;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest(classes = FiberServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Fiber API Performance Smoke Test")
@Import({FiberApiPerfIT.TestConfig.class, FiberApiPerfIT.TestSecurityConfig.class})
class FiberApiPerfIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        PolicyDecisionAuditRepository policyDecisionAuditRepository() {
            return mock(PolicyDecisionAuditRepository.class);
        }
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fiber_perf_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // JWT for test context
        registry.add("jwt.secret", () -> "test-secret-key-for-fiber-service-minimum-256-bits-required-for-hmac-sha-algorithm");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/fibers";
    }

    @Test
    @DisplayName("Measure p50/p95 latencies for core endpoints (50 iterations)")
    void shouldMeasureCoreEndpointLatencies() {
        int warmup = 10;
        int iterations = 50;
        int concurrency = 8;

        // Warm-up (sequential, ignored metrics)
        for (int i = 0; i < warmup; i++) {
            String code = "WARM-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            String id = given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                              "code": "%s",
                              "name": "Warmup Fiber",
                              "category": "NATURAL",
                              "originType": "UNKNOWN",
                              "sustainabilityType": "CONVENTIONAL"
                            }
                            """.formatted(code.toUpperCase()))
            .when()
                    .post()
            .then()
                    .statusCode(201)
                    .body("success", is(true))
                    .extract().path("data");

            given().pathParam("id", id)
            .when()
                    .get("/{id}")
            .then()
                    .statusCode(200)
                    .body("success", is(true));

            given().queryParam("query", code.substring(0, 4))
            .when()
                    .get("/search")
            .then()
                    .statusCode(200)
                    .body("success", is(true));
        }

        // Parallel CREATE
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<CreateRecord>> createFutures = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            final int idx = i;
            createFutures.add(pool.submit((Callable<CreateRecord>) () -> {
                String code = "PERF-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8);
                long t = System.nanoTime();
                String id = given()
                        .contentType(ContentType.JSON)
                        .body("""
                                {
                                  "code": "%s",
                                  "name": "Perf Test Fiber",
                                  "category": "NATURAL",
                                  "originType": "UNKNOWN",
                                  "sustainabilityType": "CONVENTIONAL"
                                }
                                """.formatted(code.toUpperCase()))
                .when()
                        .post()
                .then()
                        .statusCode(201)
                        .body("success", is(true))
                        .extract().path("data");
                long lat = elapsedMs(t);
                return new CreateRecord(id, code, lat);
            }));
        }
        List<Long> createLat = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (Future<CreateRecord> f : createFutures) {
            try {
                CreateRecord r = f.get();
                createLat.add(r.latencyMs());
                ids.add(r.id());
                codes.add(r.code());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        // Parallel GET
        List<Future<Long>> getFutures = new ArrayList<>();
        for (String id : ids) {
            getFutures.add(pool.submit((Callable<Long>) () -> {
                long t = System.nanoTime();
                given().pathParam("id", id)
                .when()
                        .get("/{id}")
                .then()
                        .statusCode(200)
                        .body("success", is(true));
                return elapsedMs(t);
            }));
        }
        List<Long> getLat = new ArrayList<>();
        for (Future<Long> f : getFutures) {
            try {
                getLat.add(f.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        // Parallel SEARCH
        List<Future<Long>> searchFutures = new ArrayList<>();
        for (String code : codes) {
            String q = code.substring(0, 4);
            searchFutures.add(pool.submit((Callable<Long>) () -> {
                long t = System.nanoTime();
                given().queryParam("query", q)
                .when()
                        .get("/search")
                .then()
                        .statusCode(200)
                        .body("success", is(true));
                return elapsedMs(t);
            }));
        }
        List<Long> searchLat = new ArrayList<>();
        for (Future<Long> f : searchFutures) {
            try {
                searchLat.add(f.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        pool.shutdown();
        try { pool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        print("CREATE", createLat);
        print("GET", getLat);
        print("SEARCH", searchLat);

        // Threshold assertions (enterprise guardrails)
        long createP95 = percentile(sorted(createLat), 0.95);
        long getP95 = percentile(sorted(getLat), 0.95);
        long searchP95 = percentile(sorted(searchLat), 0.95);

        org.junit.jupiter.api.Assertions.assertTrue(createP95 < 200,
                () -> "CREATE p95 exceeded: " + createP95 + "ms (limit 200ms)");
        org.junit.jupiter.api.Assertions.assertTrue(getP95 < 150,
                () -> "GET p95 exceeded: " + getP95 + "ms (limit 150ms)");
        org.junit.jupiter.api.Assertions.assertTrue(searchP95 < 200,
                () -> "SEARCH p95 exceeded: " + searchP95 + "ms (limit 200ms)");
    }

    private static long elapsedMs(long startNano) {
        return Duration.ofNanos(System.nanoTime() - startNano).toMillis();
    }

    private static void print(String label, List<Long> samples) {
        Collections.sort(samples);
        long p50 = percentile(samples, 0.50);
        long p95 = percentile(samples, 0.95);
        System.out.printf("[PERF] %s  p50=%dms  p95=%dms  n=%d\n", label, p50, p95, samples.size());
    }

    private static long percentile(List<Long> sortedSamples, double pct) {
        if (sortedSamples.isEmpty()) return 0L;
        int idx = (int) Math.ceil(pct * sortedSamples.size()) - 1;
        idx = Math.max(0, Math.min(idx, sortedSamples.size() - 1));
        return sortedSamples.get(idx);
    }

    private static List<Long> sorted(List<Long> values) {
        List<Long> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    private record CreateRecord(String id, String code, long latencyMs) {}
}


