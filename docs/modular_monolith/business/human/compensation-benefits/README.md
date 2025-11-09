# Compensation & Benefits Subdomain

**Responsibilities**
- Maintain salary bands, compensation cycles, one-off adjustments, and benefit plans
- Enforce eligibility rules and approvals aligned with local regulations
- Supply payroll with earnings and deduction configurations

**Inbound Inputs**
- Market data updates, comp cycle planning, benefits enrollment requests
- Localization packs for statutory and discretionary benefits

**Outbound Outputs**
- Events: `hr.compensation.adjusted`, `hr.benefit.enrollment.updated`, `hr.comp.cycle.locked`
- Data feeds for payroll and analytics systems

**Module Structure**
- `api`: compensation and benefits management endpoints (planned)
- `application`: cycle orchestration, approval workflows
- `domain`: band, benefit, adjustment aggregates plus rule policies
- `infra`: repositories, third-party provider gateways

