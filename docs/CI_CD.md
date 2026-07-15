# Backend CI and Container Release

This repository is in the development stage. It has a blocking CI quality gate and a controlled
container-release workflow, but it does not deploy to a server yet.

## Pull-request quality gate

`.github/workflows/ci.yml` runs these independent jobs in parallel:

- `Code Quality`: formatting, Checkstyle, and blocking SpotBugs analysis.
- `Dependency Security`: OWASP Dependency-Check, blocking at CVSS 7 or higher.
- `Tests & Coverage`: unit and integration tests plus the ratcheted JaCoCo line-coverage gate.

After all three pass, pull requests also build the production Dockerfile without publishing an
image. The repository must configure all four job names as required checks for `main`.

`NVD_API_KEY` is a required repository secret. A green `Validate NVD API key` step confirms that the
secret is available to Actions; GitHub does not reveal the stored value.

Dependabot pull requests use Dependabot secrets rather than normal Actions secrets. Add the same
`NVD_API_KEY` under the repository's Dependabot secrets as well, otherwise Dependabot update pull
requests will correctly stop at the secret-validation gate.

## Coverage ratchet

The long-term project target is 80% line coverage. The enforced baseline is intentionally stored as
`jacoco.coverage.minimum` in `pom.xml` so it has one source of truth. It starts at 40% and may only
move upward. Raise it in small steps as uncovered behavior receives tests; do not lower it to make a
pull request green.

Use:

```bash
make verify-coverage
```

DTOs, request/response transport classes, events, application bootstrap classes, and configuration
classes are excluded consistently from both the report and the gate. The report runs in Maven's
`verify` phase so Failsafe integration-test coverage is included.

## Container release

`.github/workflows/cd.yml` is deliberately a container release, not a deployment. It runs only for a
`v*` tag or a manual dispatch and performs this sequence:

1. Require the selected commit to be contained in `main` and validate version-tag syntax.
2. Reuse the full backend CI quality gate.
3. Build a local Linux image.
4. Block fixable HIGH and CRITICAL findings with Trivy.
5. Build and push the `linux/amd64` and `linux/arm64` image to GHCR.
6. Attach SBOM and provenance attestations.

Normal branch pushes do not publish images. A manual release receives a commit-SHA
tag; a version-tag release receives semantic-version and commit-SHA tags.

## Required GitHub settings

Configure branch protection/rulesets for `main`:

- Require pull requests; disallow direct pushes.
- Require the branch to be up to date before merging.
- Require `Code Quality`, `Dependency Security`, `Tests & Coverage`, and `Container Build`.
- Dismiss stale approvals when new commits are pushed.

These settings live in GitHub and cannot be enforced by files in this repository.

## Deferred until hosting exists

Real deployment needs a machine/environment decision first. Before enabling it, add an environment
matrix, protected production approvals, secret injection, database backup/restore, Flyway preflight,
post-deploy smoke tests, monitoring/alerts, and a tested rollback runbook.

CodeQL is also deferred until GitHub Code Security availability is confirmed for this repository.
Enabling a workflow before that check could create a permanently failing status check.
