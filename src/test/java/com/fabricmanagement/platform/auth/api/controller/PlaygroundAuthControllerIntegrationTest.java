package com.fabricmanagement.platform.auth.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
            .perform(post("/api/v1/playground/init").param("guestId", "integration-test-guest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.tenantId").exists())
            .andReturn();

    String responseStr = initResult.getResponse().getContentAsString();
    String token = JsonPath.read(responseStr, "$.token");

    // Wait a brief moment to allow tenant context to settle (if async tasks run)
    Thread.sleep(100);

    // 2. Fetch Personas (using the token we just got)
    MvcResult personasResult =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/playground/personas")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists())
            .andReturn();

    String personasStr = personasResult.getResponse().getContentAsString();
    String targetUserId =
        JsonPath.read(personasStr, "$[1].id"); // Pick second persona to impersonate

    // 3. Impersonate another user
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + targetUserId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.userId").value(targetUserId));
  }
}
