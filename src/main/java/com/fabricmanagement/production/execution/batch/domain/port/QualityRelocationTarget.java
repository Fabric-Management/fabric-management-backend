package com.fabricmanagement.production.execution.batch.domain.port;

import java.util.UUID;

/** Production-owned picker view of an eligible IWM location for QC relocation. */
public record QualityRelocationTarget(UUID id, String code, String name, String path) {}
