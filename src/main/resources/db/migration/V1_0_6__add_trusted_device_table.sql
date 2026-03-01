CREATE TABLE common_auth.common_trusted_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    user_id UUID NOT NULL,
    device_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(1000),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_trusted_device_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_trusted_device_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_trusted_device_uid ON common_auth.common_trusted_device(uid) WHERE uid IS NOT NULL;
CREATE INDEX idx_trusted_device_user_id ON common_auth.common_trusted_device(user_id);
CREATE INDEX idx_trusted_device_hash ON common_auth.common_trusted_device(device_hash);
