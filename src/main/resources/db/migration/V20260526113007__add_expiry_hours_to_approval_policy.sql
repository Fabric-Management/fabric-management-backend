ALTER TABLE common_approval.approval_policy
  ADD COLUMN expiry_hours INTEGER NOT NULL DEFAULT 48;

ALTER TABLE common_approval.approval_policy
  ADD CONSTRAINT chk_expiry_hours CHECK (expiry_hours >= 1 AND expiry_hours <= 720);

COMMENT ON COLUMN common_approval.approval_policy.expiry_hours 
  IS 'Onay isteğinin kaç saat sonra expire olacağı. Default: 48';
