# Shared address foundation — second slice

Prepared on 2 November 2026; status updated on 3 November. Frontend operator checks and repeatable
contract generation are recorded below. Current backend reports confirm address-write integration
tests, runtime OpenAPI export and zero SpotBugs findings. Commit messages for both repositories are
prepared; this slice's backend and frontend changes remain uncommitted. The agent has not committed
or pushed either repository.

## Implemented

- Ordinary CreateAddressRequest and UpdateAddressRequest accept optional placeId. AddressService
  resolves that reference server-side, compares normalised street/city/postcode/country code and
  attaches provider metadata only for a matching postal address. Mismatch returns
  ADDRESS_LOOKUP_MISMATCH (409). The surrounding write transaction rolls back on lookup failure.
  The client cannot assert coordinates or grant delivery approval.
- User creation carries second line, district, country code and reference through the common request.
- Autocomplete failures no longer masquerade as successful empty results. An explicit country is a
  restriction; absent country uses the configured soft region preference (GB by default, including
  Docker). Autocomplete and selection share a session token; details URLs encode path segments.
- Validation remains independent from Google availability for manual entry.
- No migration or new what3words dependency is introduced.

## Frontend

The canonical package is `platform/party/`, consumed through its public index. The old shared
form/search/mapper files are re-exports, not duplicated implementations. The unused former country
utility is deleted; country reference data lives in `platform/party/countries.ts`.
Account, organisation, personal user creation and new delivery addresses share postal inputs/mappers.
Authenticated onboarding reuses the inputs and optional search while retaining its own accepted-step
validation, draft and submission contract. Public registration still routes address collection to
that authenticated journey; no public generic address endpoint was opened.

Search uses the existing Combobox primitive and React Query. It debounces input, separates provider
failure from no results, ignores older selections after input changes, preserves manual entry and
uses a fresh session after successful selection. Fields preserve British building-number order and
store the second line separately. Editing postal fields clears the draft map reference. The label
states map recognition, not delivery verification. EN/TR keys were added at parity.

The snapshots were prepared additively and frontend generated with sync:spec/generate:api; the
operator must re-export from the backend application and then repeat frontend generation. A hand
prepared snapshot is not evidence of runtime OpenAPI parity.

## Validation commands

Backend root (Docker required for integration tests):

```sh
./mvnw fmt:format
./mvnw test -Dtest='AddressServiceTest,AddressVerificationTest,AddressLookupWriteTest,AddressContentTest,GoogleMapsAutocompleteTest,AssignContactAndAddressStepTest,CaptureSignupEvidenceStepTest,UserCreationServiceTest'
./mvnw verify -Dtest='*ArchTest,OpenApiSchemaNameCollisionTest' -Dit.test='AddressWriteIsolationIT,OpenApiExportIT' -DUPDATE_OPENAPI=true
```

Then frontend root:

```sh
pnpm run sync:spec
pnpm run generate:api
pnpm test --runInBand src/platform/party src/platform/lifecycle/onboarding src/domains/sales/components/new-sales-order/new-sales-order-delivery.test.tsx
pnpm run ci:local
```

Focused coverage includes ordinary map-backed creation, mismatched references, transaction rollback,
provider failures, explicit country/session request fields, second-line mapping, map-match
invalidation and stale selection. Source formatting and generation were performed; no tests, build,
Maven verification or CI were run by the agent.

## Boundaries still requiring a separate decision

- Onboarding's accepted-step contract continues to store postal content, not a provider reference.
  Search assists entry there; it does not manufacture a map-verification or delivery-approval state.
- Delivery snapshots retain their existing postal semantics. A separate immutable entrance/pin
  snapshot is not introduced by this shared-form migration.
- Existing provider coordinate/formatted-text storage remains in place. Production retention rules
  for this provider content must be settled before expanding long-lived geographic evidence.
  No raw autocomplete result list is persisted by the new frontend query adapter.

Provider references used while implementing the adapter:
https://developers.google.com/maps/documentation/places/web-service/reference/rest/v1/places/autocomplete
https://developers.google.com/maps/documentation/places/web-service/policies


## Independent review corrections — prepared

Five review findings were corrected; these changes have not been tested by the agent.

1. Delivery address Enter handling respects defaultPrevented and never treats the search combobox
   as a save input. The container regression covers a pending selection with an old complete address
   and verifies that normal Enter in a postal input still saves the subsequently selected address.
2. Retry repeats the failed selection when selection failed; search failures still retry the search.
3. SmartAddressForm consumes initialData only when its editing session mounts. resetKey explicitly
   opens a different record or discards a draft; an inline parent echo cannot reset local fields or
   close search. Account/personal/organisation editors provide record keys. The organisation edit
   dialog supplies the record directly at mount, rather than asynchronously echoing it through state.
   An administrator-shaped regression retains the label, line 2 and reference across parent echoes.
4. Reference matching also checks region/district and validates the entered country name against the
   provider country code. Known country aliases and locale display names are accepted while keeping
   the user's text unchanged; contradictory country names are rejected. This uses a supplied ISO
   code, not an inferred UI locale. A delivery second line remains user-owned entrance/unit text;
   a building-level map match is not proof of an entrance or a delivery approval. Normal-update unit
   tests cover contradictions and translated names; persistence tests prove rollback of both postal
   content and previous map metadata for country/state/district contradictions.
5. The onboarding adapter supplies all field errors and registered native-input refs/blur handlers.
   A controller ref binds the country select trigger for server-error focus. Restored overlength
   drafts show the state/district/postcode errors and focus the first invalid native field; a server
   country error focuses the select trigger.

Operator commands above still apply. The focused frontend selection can be narrowed to:

```sh
pnpm test --runInBand src/platform/party src/platform/lifecycle/onboarding/components/step-address.test.tsx src/domains/sales/components/new-sales-order/delivery-address-form.test.tsx
```

Then run ci:local for the full frontend gate. No new HTTP request/response fields were introduced
by these corrections; the second slice's authoritative backend export remains pending.


## Operator frontend run — follow-up corrections prepared

The operator reported that sync:spec/generate:api completed. The focused frontend selection then
had three passing suites and two failing suites: stale selection was applied when the query changed
before mutationFn started, and the onboarding test could not initialise i18next because its module
mock omitted initReactI18next. No successful frontend gate is recorded from that run.

Selection generation and the request payload are now captured synchronously in the click/keyboard
selection handler, before mutate() yields; retry also allocates a new generation synchronously.
The race regression additionally checks that originalInput belongs to the selection-time query.
The onboarding test now preserves real react-i18next exports while overriding useTranslation.
These corrections were formatted but not executed by the agent; rerun the focused frontend command
and then ci:local. No backend implementation or HTTP contract changed in this follow-up.


## Operator frontend rerun — select trigger ref correction prepared

The next focused run reported four passing suites and one failing suite (11 tests passed, one
failed). The remaining failure was server-error focus on the country select. SelectField declared
forwardRef but ignored its ref argument; it now forwards that ref to SelectTrigger, which already
forwards to the native button. A primitive-level regression checks focus and ref cleanup in both
stacked and border label layouts without opening the Radix popup. The agent formatted this fix but
did not run tests. Rerun select-field.test.tsx and onboarding step-address.test.tsx, then ci:local.


## Operator full frontend run — two test corrections prepared

The operator reported 472 passing suites / 3,238 passing tests and two failing suites in the main
phase. The heavy phase had only started in the supplied output, so the full gate is not recorded
as passed. The failures were an omitted allowed additive party.addressSearch key and a five-second
shell-preview cache test timeout.

The historical key-output snapshot remains frozen. The key inventory now explicitly includes the
new factory, with separate tests for query/country/search-session cache separation. The shell preview
cache test now consumes real session/navigation-preference hooks through a small AppShell boundary
probe. The actual AppShell presentation remains covered in its own integration suite. It checks both
missing and pre-existing preview preferences, live-session restoration, and blocks unmatched network
requests with strictServer. No timeout was increased. Source formatting was performed; tests were
not run by the agent. Production code and the HTTP contract were not changed by this follow-up.


## Operator production build — translation key typing correction prepared

The operator reported successful application compilation followed by four TypeScript errors in
party address field labels, validation-message translation and dynamically constructed type labels.
The build did not pass. Field metadata and label overrides now retain TranslationKey types; the
validation failure is a literal key; address types are derived from CreateAddressRequest and mapped
exhaustively to checked translation keys. WORKSITE/REMOTE labels were added to both EN and TR.
No any/never casts or translation type-check suppression was added. Source formatting was performed;
operator typecheck and ci:local remain pending. No backend/API contract changed in this follow-up.

## Operator complete frontend run — quality corrections prepared (2026-11-03)

The operator's `/tmp/fabric-frontend-ci.log` reports all 474 main-phase suites / 3,242 tests and
all 19 heavy-phase suites / 900 tests passed: 493 suites / 4,142 tests in total. Typecheck,
application build and Docker image build also passed. The overall local run failed at three
steps: Git differences in the address contract/generated types, ESLint and Knip.

The 25 lint warnings comprised import ordering, missing ARIA attributes in two Combobox test
doubles and an unused communication-endpoints import. These source issues are corrected; the
test doubles now identify their controlled listbox and collapsed state. Targeted ESLint source
autofix/pruning completed with exit code 0. It only reduced the existing contact-address service's
boundary count from two to one and removed its obsolete file-length suppression; no suppression
budget or guard exclusion was added. Source formatting was applied. The unreferenced legacy
`shared/utils/countries.ts` compatibility file, the only Knip file error, is deleted; the canonical
country reference data remains in `platform/party/countries.ts`. Existing Knip export/type warnings
retain their configured severity and are not suppressed by this change.

Full-tree operator lint, dead-code and suppression checks remain pending after this cleanup. No
test, build or full verification run was performed by the agent. The contract Git-difference step
continues to compare the working tree with the Git index; the intended uncommitted address contract
changes therefore still make that step fail. It was not bypassed, staged or declared passed. No
backend code or API contract changed in this quality follow-up.

```bash
# fabric-management-frontend
pnpm run lint && pnpm run check:dead && pnpm run check:suppressions
```

## Latest closure evidence and remaining work (2026-11-03)

After the quality cleanup, the operator reported the chained lint/dead-code/suppression command
reaching its successful final check: comparison base 39c581843ce6b1e3ee69eab9c47887796bb9eefc,
"Suppression ledger: no new debt". A further operator run copied both frontend contract artefacts,
ran sync:spec/generate:api, and compared both results with their saved copies. Both comparisons
were identical. This proves synchronisation and repeatable generation against the current backend
snapshot; it does not independently prove that the snapshot matches the running backend.

Existing backend Surefire reports dated 3 November show the eight focused address/signup/user
suites passing (69 tests, zero failures/errors/skips), plus three schema-name-collision tests.
These reports postdate the current address implementation and its regression sources. No current
Failsafe reports for AddressWriteIsolationIT/OpenApiExportIT were found in target, and the available
spotbugsTemp.xml is empty. Neither integration verification nor the final SpotBugs gate can be
declared passed from those files. Run the documented backend verify command and retain its result.
If the runtime export changes the contract, sync/generate the frontend again and verify the effect.

Frontend commit preparation passed the commit script's DRY_RUN checks on fatih; no files were
staged or committed. Finish backend validation and commit before the frontend contract consumer.
Real-provider manual acceptance across onboarding, account/organisation addresses and delivery
entry has not been recorded. Separate entrance pins, provider-reference persistence in onboarding
and a provider-content retention decision remain explicitly outside this implementation slice.

## Commit preparation — latest backend and frontend evidence (2026-11-03)

The operator subsequently ran the backend OpenAPI verification and then repeated frontend
sync:spec/generate:api successfully. The operator also reports a clean frontend typecheck.
Current backend reports now contain the previously missing evidence:

- AddressWriteIsolationIT: seven tests, zero failures/errors/skips (report at 13:52).
- OpenApiExportIT: one test, zero failures/errors/skips (report at 13:53); the exported snapshot
  has the same update time.
- Failsafe summary: eight completed, zero failures/errors/skips, no timeout.
- OpenApiSchemaNameCollisionTest: three tests, zero failures/errors/skips (report at 13:50).
- spotbugsXml.xml: zero BugInstance entries (report at 13:55).

These are existing operator-run artefacts inspected by the agent; the agent did not rerun Maven,
Jest, the build or typecheck. They supersede the earlier absence of integration/export/SpotBugs
evidence. Real-provider manual acceptance remains unrecorded. Backend and frontend commit messages
are prepared for the project commit script, in that order; no commit or push was executed.
