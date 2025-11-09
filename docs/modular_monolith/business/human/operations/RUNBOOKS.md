# HR Operational Runbooks

## Payroll Lock/Unlock
1. Verify no pending approvals.
2. Execute `POST /internal/hr/payroll/{payRunId}/lock`.
3. On failure, inspect logs (`hr.payroll.run` span).
4. Unlock via `POST .../unlock` with justification (audited).

## Failed Leave Accrual Retry
1. Locate failed job ID from metrics.
2. Requeue using admin endpoint (planned).
3. Validate new balance vs. expected.

## Rule Pack Publish Preview
1. Admin loads diff UI, reviews changes.
2. Run sandbox validation.
3. Obtain dual approval (HR_ADMIN + Compliance).
4. Publish → monitor cache eviction logs.

## Event Replay
- Use Kafka offset reset to replay from stored checkpoint.
- Ensure idempotency keys prevent duplicate side-effects.

