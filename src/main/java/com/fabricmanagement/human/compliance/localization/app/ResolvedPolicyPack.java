package com.fabricmanagement.human.compliance.localization.app;

import java.util.List;

public record ResolvedPolicyPack(
    String packCode,
    Integer packVersion,
    String resolvedPayload,
    List<String> lineageCodes
) {
}

