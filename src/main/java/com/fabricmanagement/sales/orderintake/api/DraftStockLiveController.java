package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.security.JwtTokenExtractor;
import com.fabricmanagement.platform.realtime.app.LiveStreamOpenApiCustomizer;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.dto.LiveClosedDto;
import com.fabricmanagement.platform.realtime.dto.LiveInvalidatedDto;
import com.fabricmanagement.platform.realtime.dto.LiveReadyDto;
import com.fabricmanagement.sales.orderintake.app.DraftStockLiveRevisionSource;
import com.fabricmanagement.sales.salesorder.app.SalesOrderLiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Draft stock selections")
public class DraftStockLiveController {
  private final LiveStreamService streams;
  private final DraftStockLiveRevisionSource source;
  private final SalesOrderLiveService authentication;

  @GetMapping(
      com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys.LIVE_EVENTS_TEMPLATE)
  @PreAuthorize(
      "@auth.can(authentication, 'sales', 'read') or @auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "subscribeDraftStockLiveEvents",
      summary = "Subscribe to roll availability and provisional ownership changes",
      description =
          "Uses the shared SSE channel. Reads current access and stock revision on each check. Ready/reconnect means read stock again; events carry no owner or lease tokens.",
      extensions =
          @Extension(
              name = LiveStreamOpenApiCustomizer.EXTENSION,
              properties = @ExtensionProperty(name = "frames", value = "ready,invalidated,closed")))
  @ApiResponse(
      responseCode = "200",
      description = "Shared live change stream",
      content =
          @Content(
              mediaType = LiveStreamOpenApiCustomizer.EVENT_STREAM,
              schema =
                  @Schema(
                      anyOf = {LiveReadyDto.class, LiveInvalidatedDto.class, LiveClosedDto.class})))
  @ApiResponse(
      responseCode = "403",
      description = "Current sales read access refused",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema =
                  @Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)))
  @ApiResponse(
      responseCode = "404",
      description = "Product not visible in this tenant",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema =
                  @Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)))
  @ApiResponse(
      responseCode = "429",
      description = "Shared connection capacity reached; honour Retry-After",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema =
                  @Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)),
      headers =
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "Retry-After",
              schema = @Schema(type = "integer", minimum = "1")))
  @ApiResponse(
      responseCode = "503",
      description = "Live channel temporarily unavailable; honour Retry-After",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema =
                  @Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)),
      headers =
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "Retry-After",
              schema = @Schema(type = "integer", minimum = "1")))
  public void subscribe(
      @PathVariable UUID productId,
      Authentication principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    streams.subscribe(
        authentication.actor(principal, JwtTokenExtractor.extract(request)),
        source,
        productId,
        request,
        response);
  }
}
