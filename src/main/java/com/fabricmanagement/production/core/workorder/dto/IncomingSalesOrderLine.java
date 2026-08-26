package com.fabricmanagement.production.core.workorder.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IncomingSalesOrderLine(
    UUID lineId,
    String productCode,
    BigDecimal quantity,
    String unit,
    LocalDate requestedDeliveryDate) {}
