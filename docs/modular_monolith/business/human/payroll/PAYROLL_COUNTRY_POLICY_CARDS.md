# Payroll Country Policy Cards (Initial)

## Turkey (TR)
- **Tax Profile:** Progressive income tax tiers (15%, 20%, 27%, 35%, 40%).
- **Social Security:** SGK employer/employee rates applied to capped base.
- **Minimum Wage Check:** Enforce net pay ≥ monthly minimum.
- **Benefits Handling:** Meal card stipends pre-tax up to legal limit.
- **Test Vector:** Employee earning 30,000 TRY gross, no allowances → expected net 22,750 TRY (draft).

## United States (US)
- **Tax Profile:** Federal tax tables (single/married) + FICA, Medicare.
- **State Overrides:** parameterized in rule pack (initial: CA, NY).
- **Benefits:** 401k pre-tax contributions (employee), employer match flagged.
- **Minimum Wage:** Validate hourly rate ≥ state minimum.
- **Test Vector:** Salary 70,000 USD, California, 5% 401k → net 3,800 USD per pay (bi-weekly).

## Acceptance Tests
- Deterministic fixtures using fixed calendar dates.
- Validate gross-to-net outputs across border cases (bonus, unpaid leave).
- Ensure tax rounding uses bankers rounding per jurisdiction.

