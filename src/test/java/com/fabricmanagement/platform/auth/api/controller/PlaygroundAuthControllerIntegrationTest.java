package com.fabricmanagement.platform.auth.api.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class PlaygroundAuthControllerIntegrationTest extends AbstractCostingIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Full Playground flow: Init -> Impersonate")
  void playgroundFlowTest() throws Exception {
    // 1. Initialize playground
    MvcResult initResult =
        mockMvc
            .perform(
                post("/api/v1/playground/init")
                    .param("guestId", "integration-test-guest")
                    .with(
                        request -> {
                          request.setRemoteAddr("10.99.99.10");
                          return request;
                        }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.tenantId").exists())
            .andExpect(jsonPath("$.organizationType").exists())
            .andReturn();

    String responseStr = initResult.getResponse().getContentAsString();
    String token = JsonPath.read(responseStr, "$.token");

    // 2. Fetch Personas (using the token we just got)
    MvcResult personasResult =
        mockMvc
            .perform(get("/api/v1/playground/personas").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[0].userType").exists())
            .andExpect(jsonPath("$[0].organizationName").exists())
            .andReturn();

    String personasStr = personasResult.getResponse().getContentAsString();
    String targetUserId = JsonPath.read(personasStr, "$[1].id");

    // 3. Impersonate another user
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + targetUserId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.userId").value(targetUserId));
  }

  @Test
  @DisplayName("Should return 429 when initializing too frequently from same IP")
  void shouldReturn429OnRateLimit() throws Exception {
    mockMvc.perform(
        post("/api/v1/playground/init")
            .param("guestId", "guest-rate-1")
            .with(
                request -> {
                  request.setRemoteAddr("10.99.99.20");
                  return request;
                }));
    mockMvc
        .perform(
            post("/api/v1/playground/init")
                .param("guestId", "guest-rate-2")
                .with(
                    request -> {
                      request.setRemoteAddr("10.99.99.20");
                      return request;
                    }))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("Should return 401 when impersonating with invalid token")
  void shouldReturn401ForInvalidToken() throws Exception {
    // Generate a regular (non-playground) token - For this integration test we can't easily
    // inject a fake token unless we use JwtService. We will just use an invalid token or missing
    // token.
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + UUID.randomUUID())
                .header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
  }
}
