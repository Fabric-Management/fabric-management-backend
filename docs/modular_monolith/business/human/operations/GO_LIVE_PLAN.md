# HR Go-Live Plan (TR & US)

## Shadow Phase

- Run policy packs in evaluation mode for one payroll cycle.
- Compare leave balances, payroll outputs with legacy spreadsheets.
- Log discrepancies > 1% for remediation.

## Enablement Checklist

- ✅ TR pack active, leave accrual validated.
- ✅ US pack active, payroll golden tests pass.
- ✅ HR admins trained on rule pack UI.
- ✅ Monitoring dashboards configured.

## Production Cutover

1. Freeze legacy HR changes 24h prior.
2. Publish final policy packs.
3. Switch API Gateway routes to new HR endpoints.
4. Monitor metrics and alert channels for 48h stabilisation.

## Sign-off

- HR Director approval
- Finance controller confirmation
- Engineering on-call acknowledgement
