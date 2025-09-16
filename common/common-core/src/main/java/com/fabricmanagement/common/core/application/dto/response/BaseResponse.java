package com.fabricmanagement.common.core.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Base response class for all API responses
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseResponse {

    private LocalDateTime timestamp;

    protected BaseResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
