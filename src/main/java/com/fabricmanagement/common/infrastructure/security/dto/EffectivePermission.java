package com.fabricmanagement.common.infrastructure.security.dto;

import com.fabricmanagement.platform.user.domain.DataScope;

public record EffectivePermission(String resource, String action, DataScope scope) {}
