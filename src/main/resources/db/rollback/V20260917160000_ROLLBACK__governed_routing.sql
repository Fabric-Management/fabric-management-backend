-- Remove routing before removing notification metadata; destroys routing configuration/history.
DROP TABLE flowboard.routing_failure_alert;
DROP TABLE flowboard.routing_failure_resolution;
DROP TABLE flowboard.routing_failure;
DROP TABLE flowboard.routing_task_state;
DROP TABLE flowboard.routing_pool_member;
DROP TABLE flowboard.routing_pool;
DROP FUNCTION flowboard.reject_routing_ledger_mutation();
DROP INDEX flowboard.uq_routing_task_tenant_id;
DROP INDEX common_user.uq_routing_user_tenant_id;
DROP INDEX notification.uq_notification_delivery_key;
ALTER TABLE notification.notification_log DROP COLUMN delivery_key;
DELETE FROM notification.notification_template WHERE event_type = 'ROUTING_FAILURE';
DELETE FROM i18n.translation_value WHERE translation_key_id IN
    (SELECT id FROM i18n.translation_key WHERE key_code IN
        ('notification.routing_failure.title','notification.routing_failure.body'));
DELETE FROM i18n.translation_key WHERE key_code IN
    ('notification.routing_failure.title','notification.routing_failure.body');
