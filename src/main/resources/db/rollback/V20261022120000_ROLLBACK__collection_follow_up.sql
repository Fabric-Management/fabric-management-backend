-- Development-only destructive rollback; preserve contacts before use.
DROP TABLE IF EXISTS flowboard.collection_follow_up_command;
DROP TABLE IF EXISTS flowboard.collection_contact;
DROP TABLE IF EXISTS flowboard.collection_follow_up_scan;
DROP TABLE IF EXISTS flowboard.collection_follow_up;
DROP FUNCTION IF EXISTS flowboard.guard_collection_follow_up_evidence();
-- Retain widened task/pool vocabulary: historical governed tasks must not be invalidated.
