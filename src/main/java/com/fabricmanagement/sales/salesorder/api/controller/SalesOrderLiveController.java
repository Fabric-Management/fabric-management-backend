package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.JwtTokenExtractor;
import com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail;
import com.fabricmanagement.platform.realtime.app.LiveStreamOpenApiCustomizer;
import com.fabricmanagement.platform.realtime.dto.LiveClosedDto;
import com.fabricmanagement.platform.realtime.dto.LiveInvalidatedDto;
import com.fabricmanagement.platform.realtime.dto.LiveReadyDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderLiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live change notifications of one sales order (CEDIT-05). A signal only says "read the order
 * again"; it carries no field values, people or edit data, and it is not proof that anyone saw a
 * change or received a save's answer.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Live Events", description = "Change signals of an open sales order")
public class SalesOrderLiveController {

  static final String PATH = "/api/v1/sales/orders/{orderId}/live-events";

  private final SalesOrderLiveService live;

  @GetMapping(PATH)
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "subscribeSalesOrderLiveEvents",
      summary = "Subscribe to change signals of a sales order",
      description =
          "Server-Sent Events. Authentication is the normal access token (cookie or Authorization"
              + " header); nothing in the query string is read. Requires current read access to"
              + " this order, checked again on every poll; write access or a draft is not"
              + " required. The first frame is always `ready`; `invalidated` means the committed"
              + " revision changed and the client reads the order again; `closed` ends the stream"
              + " with a reason. Changes may coalesce and nothing is replayed: on every reconnect"
              + " the client starts from `ready` and reads the current order. Lines starting with"
              + " `:` are keepalive comments. Refusals before the stream opens use the normal"
              + " problem response.",
      extensions =
          @Extension(
              name = LiveStreamOpenApiCustomizer.EXTENSION,
              properties = @ExtensionProperty(name = "frames", value = "ready,invalidated,closed")))
  @Parameter(
      name = "Last-Event-ID",
      in = ParameterIn.HEADER,
      required = false,
      description =
          "Ignored. Frames have no ids and are never replayed; every connection starts with"
              + " `ready` and the client reads the current state.",
      schema = @Schema(type = "string"))
  @ApiResponse(
      responseCode = "200",
      description = "The event stream; `ready` is its first frame",
      headers = {
        @Header(
            name = "Cache-Control",
            description = "Always no-store",
            schema = @Schema(type = "string", example = "no-store")),
        @Header(
            name = "X-Accel-Buffering",
            description = "Asks reverse proxies not to buffer the stream",
            schema = @Schema(type = "string", example = "no"))
      },
      content =
          @Content(
              mediaType = LiveStreamOpenApiCustomizer.EVENT_STREAM,
              schema =
                  @Schema(
                      anyOf = {LiveReadyDto.class, LiveInvalidatedDto.class, LiveClosedDto.class}),
              examples = {
                @ExampleObject(
                    name = "ready",
                    summary = "First frame of every connection",
                    value =
                        "event:ready\n"
                            + "data:{\"success\":true,\"data\":{\"connectionId\":"
                            + "\"5f720a32-85d2-4af9-a762-c91f7423eaf2\",\"resourceId\":"
                            + "\"30dfd4f0-e5d6-49cb-a539-75347f083441\",\"revision\":\"12\"},"
                            + "\"timestamp\":\"2026-10-07T09:00:00Z\"}\n\n"),
                @ExampleObject(
                    name = "invalidated",
                    summary = "The order changed: read it again",
                    value =
                        "event:invalidated\n"
                            + "data:{\"success\":true,\"data\":{\"connectionId\":"
                            + "\"5f720a32-85d2-4af9-a762-c91f7423eaf2\",\"resourceId\":"
                            + "\"30dfd4f0-e5d6-49cb-a539-75347f083441\",\"revision\":\"13\"},"
                            + "\"timestamp\":\"2026-10-07T09:00:04Z\"}\n\n"),
                @ExampleObject(
                    name = "closed",
                    summary = "The server ends the stream",
                    value =
                        "event:closed\n"
                            + "data:{\"success\":true,\"data\":{\"connectionId\":"
                            + "\"5f720a32-85d2-4af9-a762-c91f7423eaf2\",\"reason\":"
                            + "\"RECONNECT_REQUIRED\"},\"timestamp\":\"2026-10-07T09:01:00Z\"}\n\n"),
                @ExampleObject(
                    name = "keepalive",
                    summary = "Keepalive comment, not data",
                    value = ": keepalive\n\n")
              }))
  @ApiResponse(
      responseCode = "401",
      description = "Missing, invalid, pre-auth or expired access token, or a token without tenant",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ApiProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "No sales read permission, or a partner account",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ApiProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such active order within the caller's tenant and read scope",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ApiProblemDetail.class)))
  @ApiResponse(
      responseCode = "429",
      description = "Too many live connections on this server; retry after the given seconds",
      headers =
          @Header(
              name = "Retry-After",
              description = "Seconds to wait before retrying",
              schema = @Schema(type = "integer")),
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ApiProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description =
          "Live events are switched off (LIVE_STREAM_DISABLED) or temporarily unavailable;"
              + " editing works without them",
      headers =
          @Header(
              name = "Retry-After",
              description = "Seconds to wait before retrying",
              schema = @Schema(type = "integer")),
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ApiProblemDetail.class)))
  public void subscribeSalesOrderLiveEvents(
      @PathVariable UUID orderId,
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    // The stream writes the response itself (asynchronous, non-blocking; CEDIT-05 R1).
    live.subscribe(orderId, authentication, JwtTokenExtractor.extract(request), request, response);
  }
}
