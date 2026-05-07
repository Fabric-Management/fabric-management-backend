package com.fabricmanagement.common.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class PlaygroundQuotaInterceptorIntegrationTest extends AbstractCostingIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Should track remaining quota and block POST requests after limit is reached")
  void quotaExceeded() throws Exception {
    // 1. Init playground
    MvcResult initResult =
        mockMvc
            .perform(post("/api/v1/playground/init").param("guestId", "quota-test-guest"))
            .andExpect(status().isOk())
            .andReturn();

    String responseStr = initResult.getResponse().getContentAsString();
    String token = JsonPath.read(responseStr, "$.token");
    String userId = JsonPath.read(responseStr, "$.userId");

    // 2. Perform exactly 500 requests to hit the limit
    // We use the impersonate endpoint because it's POST and doesn't create new DB entities (fast)
    for (int i = 0; i < 500; i++) {
      mockMvc
          .perform(
              post("/api/v1/playground/impersonate/" + userId)
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(header().exists("X-Playground-Quota-Remaining"));
    }

    // 3. The 501st request should be blocked
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("X-Playground-Quota-Remaining", "0"));
  }
}
