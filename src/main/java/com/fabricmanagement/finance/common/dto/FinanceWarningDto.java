package com.fabricmanagement.finance.common.dto;

import java.util.UUID;

public record FinanceWarningDto(String code, UUID invoiceId, String message) {}
