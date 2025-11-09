# Performance & Appraisal Subdomain

**Responsibilities**
- Configure goal frameworks, review cycles, rating scales, and calibration gates
- Capture qualitative feedback and quantitative scores for talent planning
- Provide compliance-ready audit trails for performance decisions

**Inbound Inputs**
- Cycle definitions from HR admins
- Manager feedback and peer reviews
- Localization rules for country-specific appraisal mandates

**Outbound Outputs**
- Events: `hr.performance.cycle.launched`, `hr.performance.review.submitted`, `hr.performance.rating.finalized`
- Insights for compensation planning and succession management

**Module Structure**
- `api`: performance cycle and review endpoints (planned)
- `application`: cycle orchestration and workflow coordination
- `domain`: goal, review, rating aggregates and policies
- `infra`: repositories, analytics connectors

