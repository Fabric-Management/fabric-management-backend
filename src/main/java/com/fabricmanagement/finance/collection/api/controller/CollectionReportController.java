package com.fabricmanagement.finance.collection.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.collection.app.CollectionReportService;
import com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Report;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/collection-reports")
@RequiredArgsConstructor
@Tag(name = "Finance Collection Reports")
public class CollectionReportController {
  private final CollectionReportService reports;

  @GetMapping
  @PreAuthorize("@auth.can(authentication,'finance','read')")
  @Operation(
      operationId = "getFinanceCollectionReport",
      summary =
          "Read agreed-plan evidence at an effective date and knowledge cutoff; currency totals"
              + " cover all filtered rows, excluding invoices",
      description =
          "Current finance scope applies before paging and totals. Historical voids without an"
              + " effective date produce null balances. Coverage of legacy/unprojected orders"
              + " remains unknown. Owner labels and responsibility are current. No FX conversion or"
              + " combined invoice total.")
  public ResponseEntity<ApiResponse<Report>> report(
      Authentication authentication,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant knowledgeCutoff,
      @RequestParam(required = false) UUID partnerId,
      @RequestParam(required = false) UUID responsibleUserId,
      @RequestParam(required = false) UUID orderId,
      @io.swagger.v3.oas.annotations.Parameter(
              schema = @io.swagger.v3.oas.annotations.media.Schema(pattern = "^[A-Z]{3}$"))
          @RequestParam(required = false)
          String currency,
      @io.swagger.v3.oas.annotations.Parameter(
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      allowableValues = {
                        "UNKNOWN_BALANCE",
                        "CANCELLED_REVIEW",
                        "OVER_SETTLED_REVIEW",
                        "REFUND_PENDING",
                        "SETTLED",
                        "CASH_OVERDUE",
                        "CHEQUE_DELIVERY_OVERDUE",
                        "CHEQUE_CASH_OVERDUE",
                        "CHEQUE_AWAITING_CLEARING",
                        "DUE_TODAY",
                        "FUTURE",
                        "UNDATED",
                        "DISHONOURED"
                      }))
          @RequestParam(required = false)
          String bucket,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @io.swagger.v3.oas.annotations.Parameter(
              schema = @io.swagger.v3.oas.annotations.media.Schema(minimum = "0"))
          @RequestParam(defaultValue = "0")
          int page,
      @io.swagger.v3.oas.annotations.Parameter(
              schema = @io.swagger.v3.oas.annotations.media.Schema(minimum = "1", maximum = "100"))
          @RequestParam(defaultValue = "20")
          int size) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUserContext user)
        || user.userId() == null)
      throw new AccessDeniedException("A signed-in finance user is required");
    return ResponseEntity.ok(
        ApiResponse.success(
            reports.report(
                user.userId(),
                new CollectionReportService.Filter(
                    asOfDate,
                    knowledgeCutoff,
                    partnerId,
                    responsibleUserId,
                    orderId,
                    currency,
                    bucket,
                    from,
                    to,
                    page,
                    size))));
  }
}
