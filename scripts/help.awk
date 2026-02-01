# =============================================================================
# Make help generator – sections and short descriptions from ## comments
# Usage: awk -f scripts/help.awk Makefile
# =============================================================================

BEGIN {
    # Section order and targets (space-separated)
    n = 0
    section[++n] = "Quick Start"
    targets[n] = "dev app-run health"
    section[++n] = "Setup"
    targets[n] = "setup validate-env"
    section[++n] = "Application"
    targets[n] = "app-build app-run"
    section[++n] = "Code Quality & Tests"
    targets[n] = "format format-check checkstyle spotbugs code-quality code-quality-strict lint test test-integration coverage"
    section[++n] = "Database"
    targets[n] = "db-migrate db-shell db-info db-validate db-tables db-schemas db-view-companies db-view-users db-view-subscriptions db-view-tokens db-view-all show-tables db-backup db-restore db-reset db-clean"
    section[++n] = "Docker & Infra"
    targets[n] = "up up-all down down-clean status ps restart restart-db logs logs-db logs-errors rebuild clean-docker prune"
    section[++n] = "Health & Docs"
    targets[n] = "health metrics swagger"
    section[++n] = "Dev Tools (local)"
    targets[n] = "dev-reset dev-clean-tokens dev-clean-codes dev-stats dev-tools-health quick-test"
    section[++n] = "Kafka"
    targets[n] = "kafka-topics kafka-describe kafka-consumer"
    section[++n] = "Cleanup"
    targets[n] = "clean github-cleanup github-cleanup-dry-run"
    section[++n] = "Info"
    targets[n] = "help info"
    N = n
}

/^[a-zA-Z0-9._-]+:.*## / {
    split($0, a, "## ")
    desc = a[2]
    sub(/^[ \t]+/, "", desc)
    sub(/[ \t]+$/, "", desc)
    split(a[1], b, ":")
    t = b[1]
    sub(/^[ \t]+/, "", t)
    sub(/[ \t]+$/, "", t)
    descs[t] = desc
}

END {
    for (i = 1; i <= N; i++) {
        printf "\n\033[1;34m%s\033[0m\n", section[i]
        n = split(targets[i], T, " ")
        for (j = 1; j <= n; j++) {
            key = T[j]
            if (key in descs)
                printf "  \033[36mmake %-24s\033[0m %s\n", key, descs[key]
        }
    }
    printf "\n"
}
