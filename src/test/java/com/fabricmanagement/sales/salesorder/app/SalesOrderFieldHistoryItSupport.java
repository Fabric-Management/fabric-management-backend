package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared steps of the field-history ITs (CEDIT-09): the real HTTP endpoint with real signed tokens,
 * on the safe-edit fixture (one PostgreSQL container and one context for every safe-edit IT). Saves
 * go through the real safe-edit service, so every history row read here was written by the
 * production writer.
 */
abstract class SalesOrderFieldHistoryItSupport extends SalesOrderLiveItSupport {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Autowired protected SalesOrderFieldHistoryService historyService;

  /** An HTTP answer: status and parsed body (null when empty). */
  protected record Answer(int status, JsonNode body, String text) {}

  protected Answer historyHttp(Actor actor, UUID order, Integer limit, String cursor)
      throws IOException, InterruptedException {
    return send(order, limit, cursor, "Bearer " + token(actor));
  }

  protected Answer historyHttpWithoutToken(UUID order) throws IOException, InterruptedException {
    return send(order, null, null, null);
  }

  /** The order's history as a raw query string ({@code limit=…&cursor=…}), for refusals. */
  protected Answer historyHttpRaw(Actor actor, UUID order, String query)
      throws IOException, InterruptedException {
    URI uri =
        URI.create(
            "http://localhost:"
                + port
                + "/api/v1/sales/orders/"
                + order
                + "/field-history"
                + (query == null ? "" : "?" + query));
    return exchange(uri, "Bearer " + token(actor));
  }

  /** One page as the actor; the request must succeed. Returns the envelope's data. */
  protected JsonNode page(Actor actor, Integer limit, String cursor) {
    try {
      Answer answer = historyHttp(actor, orderId, limit, cursor);
      assertThat(answer.status()).as(answer.text()).isEqualTo(200);
      assertThat(answer.body().path("success").asBoolean()).isTrue();
      return answer.body().path("data");
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  /** Every page of one chain, in order; asserts the chain's invariants on the way. */
  protected List<JsonNode> pages(Actor actor, int limit) {
    List<JsonNode> pages = new ArrayList<>();
    String cursor = null;
    long snapshot = -1;
    do {
      JsonNode page = page(actor, limit, cursor);
      if (snapshot < 0) {
        snapshot = page.path("snapshotVersion").asLong();
      }
      assertThat(page.path("snapshotVersion").asLong()).isEqualTo(snapshot);
      assertThat(page.path("items").size()).isLessThanOrEqualTo(limit);
      boolean hasMore = page.path("hasMore").asBoolean();
      assertThat(page.path("nextCursor").isNull()).isEqualTo(!hasMore);
      pages.add(page);
      cursor = hasMore ? page.path("nextCursor").asText() : null;
    } while (cursor != null);
    return pages;
  }

  /** All entries of one chain, page after page. */
  protected List<JsonNode> entries(Actor actor, int limit) {
    List<JsonNode> entries = new ArrayList<>();
    pages(actor, limit).forEach(page -> page.path("items").forEach(entries::add));
    return entries;
  }

  protected static List<String> ids(List<JsonNode> entries) {
    return entries.stream().map(entry -> entry.path("id").asText()).toList();
  }

  /** The order's history ids in the database's own page order (version, then id, descending). */
  protected List<String> databaseOrder() {
    return jdbc.queryForList(
        "SELECT id::text FROM sales_ord.order_field_change WHERE sales_order_id = ?"
            + " ORDER BY order_version DESC, id DESC",
        String.class,
        orderId);
  }

  /** The one entry of an edit key in a list of entries. */
  protected static JsonNode only(List<JsonNode> entries, String editKey) {
    List<JsonNode> found =
        entries.stream().filter(entry -> editKey.equals(entry.path("editKey").asText())).toList();
    assertThat(found).as("entries of %s", editKey).hasSize(1);
    return found.getFirst();
  }

  protected static String limitError(Answer answer) {
    return answer.body().path("errors").path("limit").asText(null);
  }

  protected static String cursorError(Answer answer) {
    return answer.body().path("errors").path("cursor").asText(null);
  }

  private Answer send(UUID order, Integer limit, String cursor, String authorization)
      throws IOException, InterruptedException {
    List<String> query = new ArrayList<>();
    if (limit != null) {
      query.add("limit=" + limit);
    }
    if (cursor != null) {
      query.add("cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
    }
    URI uri =
        URI.create(
            "http://localhost:"
                + port
                + "/api/v1/sales/orders/"
                + order
                + "/field-history"
                + (query.isEmpty() ? "" : "?" + String.join("&", query)));
    return exchange(uri, authorization);
  }

  private Answer exchange(URI uri, String authorization) throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    HttpResponse<String> response =
        HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    String text = response.body();
    JsonNode body = text == null || text.isBlank() ? null : objectMapper.readTree(text);
    return new Answer(response.statusCode(), body, text);
  }

  /** The page size limits of the contract, for tests that walk to the edges. */
  protected static final int MAX_LIMIT = SalesOrderFieldHistoryDtos.MAX_LIMIT;
}
