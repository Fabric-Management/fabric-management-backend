# 🛠️ Scripts Directory

Utility scripts for development, deployment, and maintenance.

## 📁 Structure

```
scripts/
├── README.md                  # This file
├── docker-entrypoint.sh       # Docker container entrypoint (DO NOT RUN MANUALLY)
├── setup-git-hooks.sh         # Install Git pre-commit hooks
└── hooks/
    └── pre-commit             # Pre-commit hook (migration-entity consistency)
```

## 🚀 Usage

### Code Quality Analysis

Run all code quality checks (Format → Checkstyle → SpotBugs):

```bash
./scripts/code-quality.sh
```

Or via Makefile:

```bash
make code-quality    # Run all checks
make format          # Format code only
make checkstyle      # Checkstyle only
make spotbugs        # SpotBugs only
```

**What it does:**
1. Formats code with Google Java Format
2. Runs Checkstyle (Google Style compliance)
3. Runs SpotBugs (bug detection + security)

**Reports:**
- Checkstyle: `target/checkstyle-result.xml`
- SpotBugs: `target/spotbugsXml.xml`

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
- Checks migration files for common issues (IF NOT EXISTS, etc.)
- Warns about entity-migration mismatches

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
- [Dockerfile.service](../Dockerfile.service) - Docker build configuration
- [CI/CD Workflows](../.github/workflows/) - GitHub Actions workflows

---

**Last Updated:** 2025-01-27
