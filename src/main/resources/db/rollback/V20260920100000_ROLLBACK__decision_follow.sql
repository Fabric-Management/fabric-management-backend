-- Automatic reasons are reconstructable. Explicit follows and suppressions are user decisions.
BEGIN;
SET LOCAL row_security = off;
LOCK TABLE flowboard.decision_follow, flowboard.decision_follow_suppression
    IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM flowboard.decision_follow WHERE source = 'EXPLICIT')
     OR EXISTS (SELECT 1 FROM flowboard.decision_follow_suppression) THEN
    RAISE EXCEPTION
      'Cannot roll back decision follow while explicit follows or suppressions exist';
  END IF;
END $$;

DROP TABLE flowboard.decision_follow_suppression;
DROP TABLE flowboard.decision_follow;
DROP FUNCTION flowboard.reject_decision_follow_mutation();
COMMIT;
