package com.fabricmanagement.user_service.dto.response;

import java.util.List;
import java.util.UUID;

public record BulkOperationResponse(
        int totalRequested,
        int successCount,
        int failureCount,
        List<OperationResult> results,
        String summary
) {
    public record OperationResult(
            UUID userId,
            boolean success,
            String message
    ) {}

    public static BulkOperationResponse fromResults(List<OperationResult> results) {
        int successCount = (int) results.stream().filter(OperationResult::success).count();
        int failureCount = results.size() - successCount;

        String summary = String.format(
                "%d işlemden %d tanesi başarılı, %d tanesi başarısız",
                results.size(), successCount, failureCount
        );

        return new BulkOperationResponse(
                results.size(),
                successCount,
                failureCount,
                results,
                summary
        );
    }
}
