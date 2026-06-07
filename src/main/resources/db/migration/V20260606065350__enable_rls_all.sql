-- =========================================================================
-- AUTO-GENERATED: Enable RLS on all tenant-aware tables
-- =========================================================================

-- Table: common_approval.approval_policy
ALTER TABLE common_approval.approval_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_approval.approval_policy FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_approval.approval_policy;
CREATE POLICY rls_tenant_isolation ON common_approval.approval_policy
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_approval.approval_request
ALTER TABLE common_approval.approval_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_approval.approval_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_approval.approval_request;
CREATE POLICY rls_tenant_isolation ON common_approval.approval_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_approval.user_promotion_request
ALTER TABLE common_approval.user_promotion_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_approval.user_promotion_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_approval.user_promotion_request;
CREATE POLICY rls_tenant_isolation ON common_approval.user_promotion_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_audit.common_audit_log
ALTER TABLE common_audit.common_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_audit.common_audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_audit.common_audit_log;
CREATE POLICY rls_tenant_isolation ON common_audit.common_audit_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_auth.common_auth_user
ALTER TABLE common_auth.common_auth_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.common_auth_user FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_auth.common_auth_user;
CREATE POLICY rls_tenant_isolation ON common_auth.common_auth_user
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_auth.common_refresh_token
ALTER TABLE common_auth.common_refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.common_refresh_token FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_auth.common_refresh_token;
CREATE POLICY rls_tenant_isolation ON common_auth.common_refresh_token
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_auth.common_registration_token
ALTER TABLE common_auth.common_registration_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.common_registration_token FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_auth.common_registration_token;
CREATE POLICY rls_tenant_isolation ON common_auth.common_registration_token
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_auth.common_trusted_device
ALTER TABLE common_auth.common_trusted_device ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.common_trusted_device FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_auth.common_trusted_device;
CREATE POLICY rls_tenant_isolation ON common_auth.common_trusted_device
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_auth.common_verification_code
ALTER TABLE common_auth.common_verification_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_auth.common_verification_code FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_auth.common_verification_code;
CREATE POLICY rls_tenant_isolation ON common_auth.common_verification_code
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.common_address
ALTER TABLE common_communication.common_address ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.common_address FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.common_address;
CREATE POLICY rls_tenant_isolation ON common_communication.common_address
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.common_contact
ALTER TABLE common_communication.common_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.common_contact FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.common_contact;
CREATE POLICY rls_tenant_isolation ON common_communication.common_contact
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.common_notification
ALTER TABLE common_communication.common_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.common_notification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.common_notification;
CREATE POLICY rls_tenant_isolation ON common_communication.common_notification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.common_routing_config
ALTER TABLE common_communication.common_routing_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.common_routing_config FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.common_routing_config;
CREATE POLICY rls_tenant_isolation ON common_communication.common_routing_config
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.common_verification_log
ALTER TABLE common_communication.common_verification_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.common_verification_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.common_verification_log;
CREATE POLICY rls_tenant_isolation ON common_communication.common_verification_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_communication.communication_email_outbox
ALTER TABLE common_communication.communication_email_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_communication.communication_email_outbox FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_communication.communication_email_outbox;
CREATE POLICY rls_tenant_isolation ON common_communication.communication_email_outbox
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_department
ALTER TABLE common_company.common_department ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_department FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_department;
CREATE POLICY rls_tenant_isolation ON common_company.common_department
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_feature_catalog
ALTER TABLE common_company.common_feature_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_feature_catalog FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_feature_catalog;
CREATE POLICY rls_tenant_isolation ON common_company.common_feature_catalog
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_organization
ALTER TABLE common_company.common_organization ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_organization FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_organization;
CREATE POLICY rls_tenant_isolation ON common_company.common_organization
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_organization_address
ALTER TABLE common_company.common_organization_address ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_organization_address FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_organization_address;
CREATE POLICY rls_tenant_isolation ON common_company.common_organization_address
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_organization_contact
ALTER TABLE common_company.common_organization_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_organization_contact FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_organization_contact;
CREATE POLICY rls_tenant_isolation ON common_company.common_organization_contact
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_os_definition
ALTER TABLE common_company.common_os_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_os_definition FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_os_definition;
CREATE POLICY rls_tenant_isolation ON common_company.common_os_definition
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_subscription
ALTER TABLE common_company.common_subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_subscription FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_subscription;
CREATE POLICY rls_tenant_isolation ON common_company.common_subscription
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_subscription_quota
ALTER TABLE common_company.common_subscription_quota ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_subscription_quota FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_subscription_quota;
CREATE POLICY rls_tenant_isolation ON common_company.common_subscription_quota
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.common_trading_partner
ALTER TABLE common_company.common_trading_partner ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.common_trading_partner FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.common_trading_partner;
CREATE POLICY rls_tenant_isolation ON common_company.common_trading_partner
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.organization_certification
ALTER TABLE common_company.organization_certification ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.organization_certification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.organization_certification;
CREATE POLICY rls_tenant_isolation ON common_company.organization_certification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_company.partner_trading_partner_certification
ALTER TABLE common_company.partner_trading_partner_certification ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_company.partner_trading_partner_certification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_company.partner_trading_partner_certification;
CREATE POLICY rls_tenant_isolation ON common_company.partner_trading_partner_certification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_infrastructure.document_sequence
ALTER TABLE common_infrastructure.document_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_infrastructure.document_sequence FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_infrastructure.document_sequence;
CREATE POLICY rls_tenant_isolation ON common_infrastructure.document_sequence
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_policy.common_policy
ALTER TABLE common_policy.common_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_policy.common_policy FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_policy.common_policy;
CREATE POLICY rls_tenant_isolation ON common_policy.common_policy
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_role
ALTER TABLE common_user.common_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_role FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_role;
CREATE POLICY rls_tenant_isolation ON common_user.common_role
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_user
ALTER TABLE common_user.common_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_user FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_user;
CREATE POLICY rls_tenant_isolation ON common_user.common_user
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_user_address
ALTER TABLE common_user.common_user_address ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_user_address FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_user_address;
CREATE POLICY rls_tenant_isolation ON common_user.common_user_address
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_user_contact
ALTER TABLE common_user.common_user_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_user_contact FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_user_contact;
CREATE POLICY rls_tenant_isolation ON common_user.common_user_contact
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_user_department
ALTER TABLE common_user.common_user_department ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_user_department FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_user_department;
CREATE POLICY rls_tenant_isolation ON common_user.common_user_department
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.common_user_work_location
ALTER TABLE common_user.common_user_work_location ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.common_user_work_location FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.common_user_work_location;
CREATE POLICY rls_tenant_isolation ON common_user.common_user_work_location
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.job_title_preset
ALTER TABLE common_user.job_title_preset ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.job_title_preset FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.job_title_preset;
CREATE POLICY rls_tenant_isolation ON common_user.job_title_preset
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.permission_override
ALTER TABLE common_user.permission_override ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.permission_override FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.permission_override;
CREATE POLICY rls_tenant_isolation ON common_user.permission_override
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.permission_template
ALTER TABLE common_user.permission_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.permission_template FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.permission_template;
CREATE POLICY rls_tenant_isolation ON common_user.permission_template
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.profile_update_request
ALTER TABLE common_user.profile_update_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.profile_update_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.profile_update_request;
CREATE POLICY rls_tenant_isolation ON common_user.profile_update_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: common_user.user_nav_preferences
ALTER TABLE common_user.user_nav_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE common_user.user_nav_preferences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON common_user.user_nav_preferences;
CREATE POLICY rls_tenant_isolation ON common_user.user_nav_preferences
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.cost_calculation
ALTER TABLE costing.cost_calculation ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.cost_calculation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.cost_calculation;
CREATE POLICY rls_tenant_isolation ON costing.cost_calculation
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.cost_calculation_line
ALTER TABLE costing.cost_calculation_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.cost_calculation_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.cost_calculation_line;
CREATE POLICY rls_tenant_isolation ON costing.cost_calculation_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.cost_history
ALTER TABLE costing.cost_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.cost_history FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.cost_history;
CREATE POLICY rls_tenant_isolation ON costing.cost_history
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.cost_item
ALTER TABLE costing.cost_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.cost_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.cost_item;
CREATE POLICY rls_tenant_isolation ON costing.cost_item
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.cost_template
ALTER TABLE costing.cost_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.cost_template FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.cost_template;
CREATE POLICY rls_tenant_isolation ON costing.cost_template
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.exchange_rate_cache
ALTER TABLE costing.exchange_rate_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.exchange_rate_cache FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.exchange_rate_cache;
CREATE POLICY rls_tenant_isolation ON costing.exchange_rate_cache
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.exchange_rate_snapshot
ALTER TABLE costing.exchange_rate_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.exchange_rate_snapshot FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.exchange_rate_snapshot;
CREATE POLICY rls_tenant_isolation ON costing.exchange_rate_snapshot
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.price_list
ALTER TABLE costing.price_list ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.price_list FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.price_list;
CREATE POLICY rls_tenant_isolation ON costing.price_list
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.price_list_item
ALTER TABLE costing.price_list_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.price_list_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.price_list_item;
CREATE POLICY rls_tenant_isolation ON costing.price_list_item
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: costing.volume_price_break
ALTER TABLE costing.volume_price_break ENABLE ROW LEVEL SECURITY;
ALTER TABLE costing.volume_price_break FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON costing.volume_price_break;
CREATE POLICY rls_tenant_isolation ON costing.volume_price_break
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: finance.finance_invoice
ALTER TABLE finance.finance_invoice ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.finance_invoice FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON finance.finance_invoice;
CREATE POLICY rls_tenant_isolation ON finance.finance_invoice
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: finance.finance_invoice_line
ALTER TABLE finance.finance_invoice_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.finance_invoice_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON finance.finance_invoice_line;
CREATE POLICY rls_tenant_isolation ON finance.finance_invoice_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.automation_rule
ALTER TABLE flowboard.automation_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.automation_rule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.automation_rule;
CREATE POLICY rls_tenant_isolation ON flowboard.automation_rule
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.board
ALTER TABLE flowboard.board ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.board FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.board;
CREATE POLICY rls_tenant_isolation ON flowboard.board
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.board_group
ALTER TABLE flowboard.board_group ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.board_group FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.board_group;
CREATE POLICY rls_tenant_isolation ON flowboard.board_group
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.board_view
ALTER TABLE flowboard.board_view ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.board_view FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.board_view;
CREATE POLICY rls_tenant_isolation ON flowboard.board_view
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.dashboard_config
ALTER TABLE flowboard.dashboard_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.dashboard_config FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.dashboard_config;
CREATE POLICY rls_tenant_isolation ON flowboard.dashboard_config
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.dashboard_widget
ALTER TABLE flowboard.dashboard_widget ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.dashboard_widget FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.dashboard_widget;
CREATE POLICY rls_tenant_isolation ON flowboard.dashboard_widget
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.escalation_log
ALTER TABLE flowboard.escalation_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.escalation_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.escalation_log;
CREATE POLICY rls_tenant_isolation ON flowboard.escalation_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.recurring_task_template
ALTER TABLE flowboard.recurring_task_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.recurring_task_template FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.recurring_task_template;
CREATE POLICY rls_tenant_isolation ON flowboard.recurring_task_template
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task
ALTER TABLE flowboard.task ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task;
CREATE POLICY rls_tenant_isolation ON flowboard.task
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_activity_log
ALTER TABLE flowboard.task_activity_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_activity_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_activity_log;
CREATE POLICY rls_tenant_isolation ON flowboard.task_activity_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_assignee
ALTER TABLE flowboard.task_assignee ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_assignee FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_assignee;
CREATE POLICY rls_tenant_isolation ON flowboard.task_assignee
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_attachment
ALTER TABLE flowboard.task_attachment ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_attachment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_attachment;
CREATE POLICY rls_tenant_isolation ON flowboard.task_attachment
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_checklist
ALTER TABLE flowboard.task_checklist ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_checklist FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_checklist;
CREATE POLICY rls_tenant_isolation ON flowboard.task_checklist
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_comment
ALTER TABLE flowboard.task_comment ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_comment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_comment;
CREATE POLICY rls_tenant_isolation ON flowboard.task_comment
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_dependency
ALTER TABLE flowboard.task_dependency ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_dependency FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_dependency;
CREATE POLICY rls_tenant_isolation ON flowboard.task_dependency
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_label
ALTER TABLE flowboard.task_label ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_label FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_label;
CREATE POLICY rls_tenant_isolation ON flowboard.task_label
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_label_assignment
ALTER TABLE flowboard.task_label_assignment ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_label_assignment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_label_assignment;
CREATE POLICY rls_tenant_isolation ON flowboard.task_label_assignment
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_relation
ALTER TABLE flowboard.task_relation ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_relation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_relation;
CREATE POLICY rls_tenant_isolation ON flowboard.task_relation
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_reminder
ALTER TABLE flowboard.task_reminder ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_reminder FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_reminder;
CREATE POLICY rls_tenant_isolation ON flowboard.task_reminder
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_template
ALTER TABLE flowboard.task_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_template FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_template;
CREATE POLICY rls_tenant_isolation ON flowboard.task_template
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.task_time_entry
ALTER TABLE flowboard.task_time_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.task_time_entry FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.task_time_entry;
CREATE POLICY rls_tenant_isolation ON flowboard.task_time_entry
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: flowboard.user_performance_snapshot
ALTER TABLE flowboard.user_performance_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.user_performance_snapshot FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON flowboard.user_performance_snapshot;
CREATE POLICY rls_tenant_isolation ON flowboard.user_performance_snapshot
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_employee
ALTER TABLE human.human_employee ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_employee FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_employee;
CREATE POLICY rls_tenant_isolation ON human.human_employee
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_employee_number_sequence
ALTER TABLE human.human_employee_number_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_employee_number_sequence FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_employee_number_sequence;
CREATE POLICY rls_tenant_isolation ON human.human_employee_number_sequence
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_holiday_calendar
ALTER TABLE human.human_holiday_calendar ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_holiday_calendar FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_holiday_calendar;
CREATE POLICY rls_tenant_isolation ON human.human_holiday_calendar
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_hr_country_pack_mapping
ALTER TABLE human.human_hr_country_pack_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_hr_country_pack_mapping FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_hr_country_pack_mapping;
CREATE POLICY rls_tenant_isolation ON human.human_hr_country_pack_mapping
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_hr_policy_binding
ALTER TABLE human.human_hr_policy_binding ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_hr_policy_binding FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_hr_policy_binding;
CREATE POLICY rls_tenant_isolation ON human.human_hr_policy_binding
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_hr_policy_pack
ALTER TABLE human.human_hr_policy_pack ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_hr_policy_pack FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_hr_policy_pack;
CREATE POLICY rls_tenant_isolation ON human.human_hr_policy_pack
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_hr_rule_audit_log
ALTER TABLE human.human_hr_rule_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_hr_rule_audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_hr_rule_audit_log;
CREATE POLICY rls_tenant_isolation ON human.human_hr_rule_audit_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_hr_rule_version
ALTER TABLE human.human_hr_rule_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_hr_rule_version FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_hr_rule_version;
CREATE POLICY rls_tenant_isolation ON human.human_hr_rule_version
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_leave_accrual_log
ALTER TABLE human.human_leave_accrual_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_leave_accrual_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_leave_accrual_log;
CREATE POLICY rls_tenant_isolation ON human.human_leave_accrual_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_leave_balance
ALTER TABLE human.human_leave_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_leave_balance FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_leave_balance;
CREATE POLICY rls_tenant_isolation ON human.human_leave_balance
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_leave_type
ALTER TABLE human.human_leave_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_leave_type FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_leave_type;
CREATE POLICY rls_tenant_isolation ON human.human_leave_type
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_pay_period
ALTER TABLE human.human_pay_period ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_pay_period FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_pay_period;
CREATE POLICY rls_tenant_isolation ON human.human_pay_period
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_pay_run
ALTER TABLE human.human_pay_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_pay_run FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_pay_run;
CREATE POLICY rls_tenant_isolation ON human.human_pay_run
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_pay_run_audit_log
ALTER TABLE human.human_pay_run_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_pay_run_audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_pay_run_audit_log;
CREATE POLICY rls_tenant_isolation ON human.human_pay_run_audit_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_pay_run_entry
ALTER TABLE human.human_pay_run_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_pay_run_entry FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_pay_run_entry;
CREATE POLICY rls_tenant_isolation ON human.human_pay_run_entry
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: human.human_pay_run_payout
ALTER TABLE human.human_pay_run_payout ENABLE ROW LEVEL SECURITY;
ALTER TABLE human.human_pay_run_payout FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON human.human_pay_run_payout;
CREATE POLICY rls_tenant_isolation ON human.human_pay_run_payout
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: i18n.supported_locale
ALTER TABLE i18n.supported_locale ENABLE ROW LEVEL SECURITY;
ALTER TABLE i18n.supported_locale FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON i18n.supported_locale;
CREATE POLICY rls_tenant_isolation ON i18n.supported_locale
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: i18n.tenant_locale_config
ALTER TABLE i18n.tenant_locale_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE i18n.tenant_locale_config FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON i18n.tenant_locale_config;
CREATE POLICY rls_tenant_isolation ON i18n.tenant_locale_config
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: i18n.translation_key
ALTER TABLE i18n.translation_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE i18n.translation_key FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON i18n.translation_key;
CREATE POLICY rls_tenant_isolation ON i18n.translation_key
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: i18n.translation_value
ALTER TABLE i18n.translation_value ENABLE ROW LEVEL SECURITY;
ALTER TABLE i18n.translation_value FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON i18n.translation_value;
CREATE POLICY rls_tenant_isolation ON i18n.translation_value
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: i18n.user_locale_config
ALTER TABLE i18n.user_locale_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE i18n.user_locale_config FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON i18n.user_locale_config;
CREATE POLICY rls_tenant_isolation ON i18n.user_locale_config
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.lot_end_rule
ALTER TABLE iwm.lot_end_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.lot_end_rule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.lot_end_rule;
CREATE POLICY rls_tenant_isolation ON iwm.lot_end_rule
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.min_stock_rule
ALTER TABLE iwm.min_stock_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.min_stock_rule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.min_stock_rule;
CREATE POLICY rls_tenant_isolation ON iwm.min_stock_rule
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.return_rate_rule
ALTER TABLE iwm.return_rate_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.return_rate_rule FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.return_rate_rule;
CREATE POLICY rls_tenant_isolation ON iwm.return_rate_rule
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.rma
ALTER TABLE iwm.rma ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.rma FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.rma;
CREATE POLICY rls_tenant_isolation ON iwm.rma
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.rma_line
ALTER TABLE iwm.rma_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.rma_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.rma_line;
CREATE POLICY rls_tenant_isolation ON iwm.rma_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_adjustment_request
ALTER TABLE iwm.stock_adjustment_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_adjustment_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_adjustment_request;
CREATE POLICY rls_tenant_isolation ON iwm.stock_adjustment_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_count
ALTER TABLE iwm.stock_count ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_count FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_count;
CREATE POLICY rls_tenant_isolation ON iwm.stock_count
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_count_assignee
ALTER TABLE iwm.stock_count_assignee ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_count_assignee FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_count_assignee;
CREATE POLICY rls_tenant_isolation ON iwm.stock_count_assignee
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_count_line
ALTER TABLE iwm.stock_count_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_count_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_count_line;
CREATE POLICY rls_tenant_isolation ON iwm.stock_count_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_count_tolerance
ALTER TABLE iwm.stock_count_tolerance ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_count_tolerance FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_count_tolerance;
CREATE POLICY rls_tenant_isolation ON iwm.stock_count_tolerance
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_reservation
ALTER TABLE iwm.stock_reservation ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_reservation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_reservation;
CREATE POLICY rls_tenant_isolation ON iwm.stock_reservation
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.stock_transfer
ALTER TABLE iwm.stock_transfer ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.stock_transfer FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.stock_transfer;
CREATE POLICY rls_tenant_isolation ON iwm.stock_transfer
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: iwm.warehouse_location
ALTER TABLE iwm.warehouse_location ENABLE ROW LEVEL SECURITY;
ALTER TABLE iwm.warehouse_location FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON iwm.warehouse_location;
CREATE POLICY rls_tenant_isolation ON iwm.warehouse_location
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: logistics.logistics_shipment
ALTER TABLE logistics.logistics_shipment ENABLE ROW LEVEL SECURITY;
ALTER TABLE logistics.logistics_shipment FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON logistics.logistics_shipment;
CREATE POLICY rls_tenant_isolation ON logistics.logistics_shipment
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: logistics.logistics_shipment_line
ALTER TABLE logistics.logistics_shipment_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE logistics.logistics_shipment_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON logistics.logistics_shipment_line;
CREATE POLICY rls_tenant_isolation ON logistics.logistics_shipment_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: logistics.logistics_shipment_line_batch
ALTER TABLE logistics.logistics_shipment_line_batch ENABLE ROW LEVEL SECURITY;
ALTER TABLE logistics.logistics_shipment_line_batch FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON logistics.logistics_shipment_line_batch;
CREATE POLICY rls_tenant_isolation ON logistics.logistics_shipment_line_batch
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: notification.notification_log
ALTER TABLE notification.notification_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification.notification_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON notification.notification_log;
CREATE POLICY rls_tenant_isolation ON notification.notification_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: notification.notification_queue
ALTER TABLE notification.notification_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification.notification_queue FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON notification.notification_queue;
CREATE POLICY rls_tenant_isolation ON notification.notification_queue
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: notification.notification_template
ALTER TABLE notification.notification_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification.notification_template FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON notification.notification_template;
CREATE POLICY rls_tenant_isolation ON notification.notification_template
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: notification.user_notification_preference
ALTER TABLE notification.user_notification_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification.user_notification_preference FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON notification.user_notification_preference;
CREATE POLICY rls_tenant_isolation ON notification.user_notification_preference
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.purchase_order
ALTER TABLE procurement.purchase_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.purchase_order FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.purchase_order;
CREATE POLICY rls_tenant_isolation ON procurement.purchase_order
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.purchase_order_line
ALTER TABLE procurement.purchase_order_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.purchase_order_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.purchase_order_line;
CREATE POLICY rls_tenant_isolation ON procurement.purchase_order_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.subcontract_order
ALTER TABLE procurement.subcontract_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.subcontract_order FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.subcontract_order;
CREATE POLICY rls_tenant_isolation ON procurement.subcontract_order
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_quote
ALTER TABLE procurement.supplier_quote ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_quote FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_quote;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_quote
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_quote_line
ALTER TABLE procurement.supplier_quote_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_quote_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_quote_line;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_quote_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_quote_token
ALTER TABLE procurement.supplier_quote_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_quote_token FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_quote_token;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_quote_token
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_rfq
ALTER TABLE procurement.supplier_rfq ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_rfq FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_rfq;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_rfq
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_rfq_line
ALTER TABLE procurement.supplier_rfq_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_rfq_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_rfq_line;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_rfq_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: procurement.supplier_rfq_recipient
ALTER TABLE procurement.supplier_rfq_recipient ENABLE ROW LEVEL SECURITY;
ALTER TABLE procurement.supplier_rfq_recipient FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON procurement.supplier_rfq_recipient;
CREATE POLICY rls_tenant_isolation ON procurement.supplier_rfq_recipient
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.goods_receipt
ALTER TABLE production.goods_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.goods_receipt FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.goods_receipt;
CREATE POLICY rls_tenant_isolation ON production.goods_receipt
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.goods_receipt_item
ALTER TABLE production.goods_receipt_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.goods_receipt_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.goods_receipt_item;
CREATE POLICY rls_tenant_isolation ON production.goods_receipt_item
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.inheritance_rule_schema
ALTER TABLE production.inheritance_rule_schema ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.inheritance_rule_schema FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.inheritance_rule_schema;
CREATE POLICY rls_tenant_isolation ON production.inheritance_rule_schema
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_fiber
ALTER TABLE production.prod_fiber ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_fiber_category
ALTER TABLE production.prod_fiber_category ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber_category FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_category;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber_category
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_fiber_certification
ALTER TABLE production.prod_fiber_certification ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber_certification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_certification;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber_certification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_fiber_iso_code
ALTER TABLE production.prod_fiber_iso_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber_iso_code FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_iso_code;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber_iso_code
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_fiber_quality_standard
ALTER TABLE production.prod_fiber_quality_standard ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_fiber_quality_standard FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_fiber_quality_standard;
CREATE POLICY rls_tenant_isolation ON production.prod_fiber_quality_standard
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_product
ALTER TABLE production.prod_product ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_product FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_product;
CREATE POLICY rls_tenant_isolation ON production.prod_product
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_product_attribute
ALTER TABLE production.prod_product_attribute ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_product_attribute FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_product_attribute;
CREATE POLICY rls_tenant_isolation ON production.prod_product_attribute
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_recipe
ALTER TABLE production.prod_recipe ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_recipe FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_recipe;
CREATE POLICY rls_tenant_isolation ON production.prod_recipe
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_recipe_component
ALTER TABLE production.prod_recipe_component ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_recipe_component FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_recipe_component;
CREATE POLICY rls_tenant_isolation ON production.prod_recipe_component
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_work_order
ALTER TABLE production.prod_work_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_work_order FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_work_order;
CREATE POLICY rls_tenant_isolation ON production.prod_work_order
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_work_order_assignee
ALTER TABLE production.prod_work_order_assignee ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_work_order_assignee FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_work_order_assignee;
CREATE POLICY rls_tenant_isolation ON production.prod_work_order_assignee
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_yarn_attribute
ALTER TABLE production.prod_yarn_attribute ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_attribute FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_attribute;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_attribute
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_yarn_category
ALTER TABLE production.prod_yarn_category ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_category FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_category;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_category
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.prod_yarn_certification
ALTER TABLE production.prod_yarn_certification ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.prod_yarn_certification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.prod_yarn_certification;
CREATE POLICY rls_tenant_isolation ON production.prod_yarn_certification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_batch
ALTER TABLE production.production_execution_batch ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_batch;
CREATE POLICY rls_tenant_isolation ON production.production_execution_batch
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_batch_attribute
ALTER TABLE production.production_execution_batch_attribute ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch_attribute FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_batch_attribute;
CREATE POLICY rls_tenant_isolation ON production.production_execution_batch_attribute
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_batch_certification
ALTER TABLE production.production_execution_batch_certification ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch_certification FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_batch_certification;
CREATE POLICY rls_tenant_isolation ON production.production_execution_batch_certification
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_batch_lineage
ALTER TABLE production.production_execution_batch_lineage ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch_lineage FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_batch_lineage;
CREATE POLICY rls_tenant_isolation ON production.production_execution_batch_lineage
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_batch_reservation
ALTER TABLE production.production_execution_batch_reservation ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_batch_reservation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_batch_reservation;
CREATE POLICY rls_tenant_isolation ON production.production_execution_batch_reservation
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_inventory_balance
ALTER TABLE production.production_execution_inventory_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_inventory_balance FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_inventory_balance;
CREATE POLICY rls_tenant_isolation ON production.production_execution_inventory_balance
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_execution_inventory_transaction
ALTER TABLE production.production_execution_inventory_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_execution_inventory_transaction FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_execution_inventory_transaction;
CREATE POLICY rls_tenant_isolation ON production.production_execution_inventory_transaction
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_fiber_request
ALTER TABLE production.production_fiber_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_fiber_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_fiber_request;
CREATE POLICY rls_tenant_isolation ON production.production_fiber_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_output_item
ALTER TABLE production.production_output_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_output_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_output_item;
CREATE POLICY rls_tenant_isolation ON production.production_output_item
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_output_record
ALTER TABLE production.production_output_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_output_record FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_output_record;
CREATE POLICY rls_tenant_isolation ON production.production_output_record
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.production_quality_fiber_test_result
ALTER TABLE production.production_quality_fiber_test_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.production_quality_fiber_test_result FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.production_quality_fiber_test_result;
CREATE POLICY rls_tenant_isolation ON production.production_quality_fiber_test_result
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.quality_grade
ALTER TABLE production.quality_grade ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.quality_grade FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.quality_grade;
CREATE POLICY rls_tenant_isolation ON production.quality_grade
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.stock_unit
ALTER TABLE production.stock_unit ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.stock_unit;
CREATE POLICY rls_tenant_isolation ON production.stock_unit
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.stock_unit_audit_log
ALTER TABLE production.stock_unit_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.stock_unit_audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.stock_unit_audit_log;
CREATE POLICY rls_tenant_isolation ON production.stock_unit_audit_log
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.work_order_consumption
ALTER TABLE production.work_order_consumption ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.work_order_consumption FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.work_order_consumption;
CREATE POLICY rls_tenant_isolation ON production.work_order_consumption
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: production.work_order_output
ALTER TABLE production.work_order_output ENABLE ROW LEVEL SECURITY;
ALTER TABLE production.work_order_output FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON production.work_order_output;
CREATE POLICY rls_tenant_isolation ON production.work_order_output
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.discount_policy
ALTER TABLE sales.discount_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.discount_policy FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.discount_policy;
CREATE POLICY rls_tenant_isolation ON sales.discount_policy
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.quote
ALTER TABLE sales.quote ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.quote FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.quote;
CREATE POLICY rls_tenant_isolation ON sales.quote
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.quote_approval_token
ALTER TABLE sales.quote_approval_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.quote_approval_token FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.quote_approval_token;
CREATE POLICY rls_tenant_isolation ON sales.quote_approval_token
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.quote_line
ALTER TABLE sales.quote_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.quote_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.quote_line;
CREATE POLICY rls_tenant_isolation ON sales.quote_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.sales_product
ALTER TABLE sales.sales_product ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.sales_product FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.sales_product;
CREATE POLICY rls_tenant_isolation ON sales.sales_product
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.sample_delivery
ALTER TABLE sales.sample_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.sample_delivery FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.sample_delivery;
CREATE POLICY rls_tenant_isolation ON sales.sample_delivery
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales.sample_request
ALTER TABLE sales.sample_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales.sample_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales.sample_request;
CREATE POLICY rls_tenant_isolation ON sales.sample_request
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales_ord.sales_order
ALTER TABLE sales_ord.sales_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.sales_order FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales_ord.sales_order;
CREATE POLICY rls_tenant_isolation ON sales_ord.sales_order
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Table: sales_ord.sales_order_line
ALTER TABLE sales_ord.sales_order_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE sales_ord.sales_order_line FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS rls_tenant_isolation ON sales_ord.sales_order_line;
CREATE POLICY rls_tenant_isolation ON sales_ord.sales_order_line
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

