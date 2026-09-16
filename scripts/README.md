# 🛠️ Scripts Directory

Utility scripts for development, deployment, and maintenance.

## 📁 Structure

```
scripts/
├── README.md                  # This file
├── postgres_image.py          # Generate and guard the PostgreSQL image pin
├── test_postgres_image.py     # Red probes for PostgreSQL image governance
├── build_4_test_reports.py    # Extract and compare Surefire/Failsafe identities
├── test_build_4_test_reports.py # Report-policy regression tests
├── build_4_verify.sh           # Run and archive complete BUILD-4 verification
├── help.awk                   # Groups make help by section (used by make help)
├── docker-entrypoint.sh       # Docker container entrypoint (DO NOT RUN MANUALLY)
├── setup-git-hooks.sh         # Install Git pre-commit hooks
├── cleanup-github-actions.sh  # Clean GitHub Actions workflow runs
└── hooks/
    └── pre-commit             # Pre-commit hook (Java format + migration-entity consistency)
```

## 🚀 Usage

### Code Quality (via Makefile)

```bash
make code-quality         # Format + Checkstyle + SpotBugs (reports only, no fail)
make code-quality-strict  # Same, but fail on any violation
make format               # Format code only
make format-check         # Verify format only (no changes)
make checkstyle           # Checkstyle only
make spotbugs             # SpotBugs only
```

See [docs/CODE_QUALITY.md](../docs/CODE_QUALITY.md) for full details.

### PostgreSQL Image Governance

`pom.xml` is the single authored source for the PostgreSQL container image. Regenerate the Compose
counterpart after changing that property, then use the check mode to detect drift, hardcoded test
images, accessor bypasses, or a digest that is not a Linux amd64/arm64 manifest index:

```bash
python3 -B scripts/postgres_image.py --write
python3 -B scripts/postgres_image.py --check
```

The check reads Docker Hub's official tag metadata, so it requires network access. CI also pulls the
exact generated image before the test suite. Run all build-governance tests with:

```bash
python3 -B -m unittest discover -s scripts -p 'test_*.py'
```

### BUILD-4 Verification Evidence

The supported workflow is a single command:

```bash
make build-4-verify
```

It refuses to overwrite an existing archive. By default it reads `~/build-4-baseline`, writes the
new archive to `~/build-4-after`, and generates
`../docs/platform/evidence/build-4/after.md`. Override the archive paths when needed:

```bash
make build-4-verify \
  BUILD_4_BASELINE=/absolute/path/to/baseline \
  BUILD_4_AFTER=/absolute/path/to/new-archive
```

The command records the environment and complete command log, runs the Python governance tests and
image guard, resolves the Compose image, pulls the exact digest on the local architecture, runs
`./mvnw -B -ntp clean verify`, preserves Surefire/Failsafe and quality artifacts outside `target/`,
stores the generated comparison and its expected-delta input in the archive, and writes a SHA-256
manifest. It generates the comparison even after a Maven failure when both report directories
exist.

For lower-level report work, generate a snapshot or compare two already preserved runs:

```bash
python3 -B scripts/build_4_test_reports.py snapshot \
  ~/build-4-baseline \
  ../docs/platform/evidence/build-4/baseline.md \
  --title "BUILD-4 pre-change mixed-version baseline"

python3 -B scripts/build_4_test_reports.py compare \
  ~/build-4-baseline \
  ~/build-4-after \
  ../docs/platform/evidence/build-4/after.md \
  --title "BUILD-4 PostgreSQL 16 verification" \
  --explanations ../docs/platform/evidence/build-4/expected-deltas.json
```

The comparison uses test identity, status, and skip/failure reason. It returns a non-zero status if
a baseline identity disappears without an exact written explanation, an existing result changes, a
failure or error remains, a test identity is newly skipped, or `build.log` does not record a successful
build. Stale explanation entries also fail so an obsolete exception cannot remain silently active.
Missing logs are reported as not assessable rather than consistent.

### Setup Git Hooks

Install pre-commit hook for migration-entity consistency checks:

```bash
./scripts/setup-git-hooks.sh
```

Or via Makefile:

```bash
make setup
```

**What it does:**

- Copies `scripts/hooks/pre-commit` to `.git/hooks/pre-commit`
- Makes it executable
- **Java:** When `.java` files are staged, runs `mvn fmt:check`; commit blocked if format fails
- **Migration/Entity:** Checks migration files (IF NOT EXISTS, etc.), warns about entity-migration mismatches

**Skip hook (not recommended):**

```bash
git commit --no-verify
```

### Docker Entrypoint

**⚠️ DO NOT RUN MANUALLY** - This script is used by Docker containers.

**What it does:**

- Waits for dependencies (PostgreSQL, Kafka)
- Configures JVM options
- Starts Spring Boot application

**Usage:** Automatically executed by Docker when container starts.

### GitHub Actions Cleanup

Clean all workflow runs from GitHub Actions (useful for cleaning up old runs):

```bash
./scripts/cleanup-github-actions.sh          # Actually delete runs
./scripts/cleanup-github-actions.sh --dry-run # Preview what would be deleted
```

Or via Makefile:

```bash
make github-cleanup-dry-run    # Preview
make github-cleanup            # Actually delete (with confirmation)
```

**Prerequisites:**

- GitHub CLI (gh) installed: `brew install gh`
- Authenticated: `gh auth login`
- Repository access to `Fabric-Management/fabric-management-backend`

**What it does:**

1. Lists all workflows in the repository
2. Fetches all runs for each workflow
3. Deletes all runs (or previews in dry-run mode)
4. Shows summary of deleted runs

**⚠️ Warning:** This permanently deletes all workflow run history. Use `--dry-run` first to preview.

## 🔧 Development

### Adding New Scripts

1. Place in `scripts/` directory
2. Add shebang: `#!/bin/bash` or `#!/bin/sh`
3. Make executable: `chmod +x scripts/your-script.sh`
4. Use `set -euo pipefail` for error handling
5. Add documentation to this README

### Script Standards

- ✅ Use `set -euo pipefail` for error handling
- ✅ Use `readonly` for constants
- ✅ Add colors for output (use existing color scheme)
- ✅ Include usage comments at top
- ✅ Add Last Updated timestamp
- ✅ Validate inputs before execution

### Color Scheme

```bash
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'  # No Color
```

## 📚 Related Documentation

- [Makefile](../Makefile) - Main development commands
- [docs/CODE_QUALITY.md](../docs/CODE_QUALITY.md) - Code quality & automatic error detection overview
- [docs/LOCAL_SETUP.md](../docs/LOCAL_SETUP.md) - Local setup without Docker
- [Dockerfile.service](../Dockerfile.service) - Docker build configuration
- [CI/CD Workflows](../.github/workflows/) - GitHub Actions workflows

---

**Last Updated:** 2026-09-16
