package com.fabricmanagement.finance.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reverses a whole payment, whatever its method. {@code reversalDate} is the accounting date of the
 * reversal and must fall in an open period; the original payment is never edited.
 *
 * <p>{@code acknowledgedPaidRefund} is the amount of refunds already paid out of this payment that
 * the person has seen and accepts to leave as recorded: that money left, so reversing the receipt
 * leaves the customer owing it. The server answers with that amount until the request echoes it.
 */
public record ReversePaymentRequest(
    @NotNull LocalDate reversalDate,
    @NotBlank @Size(max = 1000) String reason,
    @PositiveOrZero
        @Schema(
            nullable = true,
            description =
                "Refunds already paid out of this payment; required to equal them when any exist")
        BigDecimal acknowledgedPaidRefund) {}
