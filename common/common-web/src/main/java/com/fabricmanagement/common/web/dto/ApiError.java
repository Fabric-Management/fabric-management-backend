package com.fabricmanagement.common.web.dto;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> details
) {
}