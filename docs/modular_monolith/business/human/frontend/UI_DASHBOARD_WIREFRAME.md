# HR Dashboard Wireframe Outline

## Layout
- **Header:** Tenant name, country selector badge, policy pack version display.
- **Cards:**
  - Compliance Alerts (list with severity tags)
  - Expiring Contracts (table)
  - Leave Balances (top employees with low balance)
  - Payroll Status (current run progress bar)
- **Sidebar:** Navigation to submodules (Employee, Leave, Payroll, Compliance)

## Empty States
- Compliance: "All clear — compliant in all countries."
- Leave: "No pending requests. Encourage employees to plan time off."
- Payroll: "Next pay run scheduled for {date}."

## Localization UI
- Country badge with tooltip describing applied policy pack.
- Tooltips for localized rules (e.g., RTT details for France).
- Wizards indicate selected policy version (`Using Policy Pack v{packVersion}`).

