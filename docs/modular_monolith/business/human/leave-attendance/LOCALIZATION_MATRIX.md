# Leave Localization Matrix (Draft)

| Country | Leave Types | Carry-over | Pro-Rata Rules | Probation Handling | Notes |
| --- | --- | --- | --- | --- | --- |
| TR | Annual, Sick Statutory, Marriage, Bereavement | Annual max 5 days | Pro-rata based on hire month | No annual leave first 1 year | Align with Turkish Labour Law |
| US | PTO, Sick (state dependent), FMLA, Jury Duty | PTO up to 40h | Monthly accrual (hours) | Company policy configurable | State overrides via pack payload |
| FR | CP (congés payés), RTT, Sick | RTT tied to working-time reduction | Monthly accrual by worked days | RTT only after activation | Requires holiday calendar alignment |

## UI Copy Guidelines
- Display rule version badge: `Policy Pack v{packVersion}`
- Tooltips for localized nuances: `"France RTT: generated when hours > 35/week"`
- Edge states: show banner when employee still in probation (`Probation ends on {date}`)

