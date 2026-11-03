# Shared address foundation

Status: prepared on 2 November 2026; operator reported successful integration/OpenAPI tests and subsequent focused regression/SpotBugs checks. Commit and frontend migration remain pending.

## Ownership and scope

FabricOS owns its postal address model in `platform/communication`. Google is an optional lookup
provider, not a delivery approval authority. Manual address creation does not call Google.
There is no what3words integration or dependency to install.

This slice unifies the existing manual, organisation/user assignment, onboarding and legacy Google
write rules. Frontend consolidation into `platform/party/` is the next slice. Existing REST paths and
request field names remain compatible. Existing mutable request DTOs retain their builders and
accessors for internal callers; new postal content is an immutable domain record.

## Postal contract

`AddressContent` is the shared write invariant for HTTP and internal callers. Required values are
streetAddress (500), city (100), country (100), addressType and label (100). Optional values are
addressLine2 (255), state (100), district (100), postalCode (20) and countryCode (two letters).
Lengths in parentheses are maximum characters. Text is trimmed; empty optional text becomes null.
Country code is uppercased when supplied. An unknown code stays absent: UI language is never a source.
Two-letter syntax validation does not establish correspondence between country name and code.

Creation requires complete postal content, even if Google returns PARTIAL. The lookup-only endpoint
can still return incomplete candidates so a later UI can ask the user to complete them.
The legacy validate-and-create endpoint requires a label when persisting, although lookup-only does
not. It defaults the address type to HOME when omitted, preserving its existing behaviour.

The existing PUT update remains a partial update: omitted/null fields stay unchanged; empty optional
postal strings clear the field. Blank required fields are rejected after merging. Contact field
semantics are unchanged. Contact-only updates do not rebuild postal content, so a historical card
with a missing label or incomplete address can still have its contact details corrected without
inventing postal information. Postal edits still require complete merged content, including a label;
new manual and lookup creations keep the same mandatory-field rules. Full replacement for onboarding
keeps its existing ownership checks.

Sales-led signup accepts partial address facts. It creates a headquarters card only when street,
city and country all contain non-whitespace text. Partial facts are retained unchanged by the existing
CaptureSignupEvidenceStep, including known-field information; signup and company contact assignment
continue. No placeholder street, city or country is manufactured.

## Write and verification rules

All new address writes pass through AddressService and AddressContent. Google lookup creation and
refresh no longer save through their own repository path. Both use DeliveryVerificationPolicy:
Google recognition never grants `sales:verify-delivery`. Existing historical/system entity defaults
are not reclassified by a migration.

Postal changes invalidate the earlier placeId, coordinates and provider-formatted text. A label-only
change retains the map match. A country-name change without a supplied replacement code clears the
old code. A Google refresh checks active/tenant ownership before calling the provider, preserves an
existing second address line, and reapplies the actor's delivery verification policy. Invalid lookup
coordinates and failed results are rejected. Rendering includes the second postal line.

Organisation-to-user work-address copying now preserves district, countryCode and addressLine2.
It creates a new card under the current actor policy instead of copying delivery approval.

## Contract preparation and validation

`api/openapi.yaml` contains a manually prepared update to the affected request schema constraints.
It is not an authoritative regenerated export. Regenerate it from the application before frontend
sync or commit; source annotations also document create/update semantics. No frontend generated files
were changed in this backend slice. No schema migration is needed.

From the backend root, the operator runs:

```sh
./mvnw fmt:format
./mvnw test -Dtest='AddressServiceTest,AddressVerificationTest,AddressLookupWriteTest,AddressContentTest,AssignContactAndAddressStepTest,CaptureSignupEvidenceStepTest,TenantOnboardingServiceTest,TenantOnboardingOrchestratorTest'
./mvnw verify -Dtest='*ArchTest,OpenApiSchemaNameCollisionTest' -Dit.test='AddressWriteIsolationIT,TenantOnboardingIntegrationTest,RlsPolicyEnforcementIT,OpenApiExportIT' -DUPDATE_OPENAPI=true
```

The integration command requires Docker/Testcontainers. AddressWriteIsolationIT proves application
ownership checks with real persistence; RlsPolicyEnforcementIT verifies RLS catalogue configuration.
It is not itself a non-superuser cross-tenant SQL access test. Broader existing tenant enforcement
regression can be run with `./mvnw verify -DskipTests=false -Dit.test=TenantIsolationIT`.
After the export, inspect the spec diff; then the frontend can sync and generate from this backend.
Build, test and format commands were not run by the implementation agent.

## Next slice

- Consolidate frontend forms and save mappings into `platform/party/`, including register/onboarding.
- Pass a server-resolved lookup reference through ordinary save flows so selected matches are not lost.
  This slice fixes the existing lookup persistence path; it does not yet add that reference to manual
  create/update request DTOs or accept client-asserted coordinates as provider evidence.
- Separate lookup completeness labels from delivery verification in the UI.
- Harden Google lookup failures, session tokens, country restriction versus bias and attribution.
- Decide provider-content storage/retention rules before expanding permanent lookup evidence.
- Preserve delivery-specific immutable snapshots independently of later address-book edits.

Regression coverage includes partial/blank sales-led signup facts with downstream evidence capture,
complete headquarters assignment, continued company contact assignment, contact-only edits of old
unlabelled/incomplete cards, tenant rejection and unchanged creation/postal-edit completeness rules.
AddressWriteIsolationIT also persists an old unlabelled card and checks contact update round-trip.
No migration or API field change was introduced by these compatibility corrections.

## Latest operator validation

On 2 November 2026 the operator reported:

- 11 integration tests passed with no failures, errors or skipped tests, including OpenApiExportIT.
  The initial overall verify run subsequently stopped on SpotBugs NP_NULL_PARAM_DEREF in the new
  replaceFromLookup method.
- After the explicit null guard was added, `./mvnw test -Dtest=AddressLookupWriteTest spotbugs:check`
  succeeded: 11 tests, no failures/errors/skips, zero SpotBugs bugs/errors, BUILD SUCCESS at 08:15 UTC.

The agent did not execute these checks. The second run validates the correction and SpotBugs;
it was a focused test/check run, not a new full-suite verify. The earlier failed verify must not be
recorded as a successful complete verify gate. The remaining Checker Framework annotation-class
messages did not fail the reported SpotBugs check.


Second slice: see [ADDRESS-FOUNDATION-2.md](ADDRESS-FOUNDATION-2.md). New code is prepared; the previous operator results do not validate the second slice.
