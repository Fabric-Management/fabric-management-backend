# Onboarding Task Templates

| Template | Applies To | Tasks | SLA |
| --- | --- | --- | --- |
| Global Default | All hires | Create accounts, Collect documents, Assign mentor | Complete in 3 days |
| Manufacturing Plant TR | Operators in Turkey | PPE briefing, SGK registration, Medical check | Complete before start date |
| US Remote Employee | US remote staff | Ship equipment, I-9 verification, Security training | Equipment shipped within 5 days |

## Automation Hooks
- Tasks generated from policy pack metadata (`onboarding.tasks` array).
- Each task includes `ownerRole`, `dueOffset`, `blocking` flag.
- Completion publishes `hr.onboarding.task.completed`.

