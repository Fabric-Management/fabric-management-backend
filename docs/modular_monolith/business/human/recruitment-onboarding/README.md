# Recruitment & Onboarding Subdomain

**Responsibilities**
- Manage requisitions, candidates, offers, and onboarding task orchestration
- Ensure onboarding checklists align with country-specific compliance requirements
- Trigger downstream provisioning (accounts, equipment) through events

**Inbound Inputs**
- Hiring requests from business units
- Offer approvals from HR managers
- Localization packs defining onboarding obligations per country/role

**Outbound Outputs**
- Events: `hr.requisition.opened`, `hr.candidate.hired`, `hr.onboarding.task.completed`
- Task assignments for cross-team fulfillment (IT, facilities, finance)

**Module Structure**
- `api`: requisition and candidate management endpoints (planned)
- `application`: onboarding orchestration services and task schedulers
- `domain`: aggregates for requisition, candidate, onboarding packs
- `infra`: persistence adapters, notification bridges

