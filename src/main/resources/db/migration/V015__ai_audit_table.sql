-- ============================================================================
-- V15: AI Audit Logging Table
-- ============================================================================
-- Tracks all AI interactions for audit, cost tracking, and analytics
-- Last Updated: 2025-01-27
-- ============================================================================

-- Create AI schema
CREATE SCHEMA IF NOT EXISTS common_ai;

CREATE TABLE IF NOT EXISTS common_ai.common_ai_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    
    user_id UUID NOT NULL,
    input_message TEXT NOT NULL,
    output_message TEXT NOT NULL,
    model VARCHAR(100) NOT NULL,
    
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    
    latency_ms INTEGER,
    finish_reason VARCHAR(50),
    
    conversation_id UUID,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

CREATE INDEX idx_ai_log_tenant ON common_ai.common_ai_log(tenant_id);
CREATE INDEX idx_ai_log_user ON common_ai.common_ai_log(user_id);
CREATE INDEX idx_ai_log_conversation ON common_ai.common_ai_log(conversation_id);
CREATE INDEX idx_ai_log_created ON common_ai.common_ai_log(created_at);

COMMENT ON TABLE common_ai.common_ai_log IS 'AI interaction audit log - tracks all FabricAI conversations';
COMMENT ON COLUMN common_ai.common_ai_log.conversation_id IS 'Optional: For grouping multi-turn conversations';

