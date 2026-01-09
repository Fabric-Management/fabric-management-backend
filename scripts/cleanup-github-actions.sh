#!/bin/bash
# =============================================================================
# GITHUB ACTIONS WORKFLOW RUNS CLEANUP SCRIPT
# =============================================================================
# Deletes all workflow runs from GitHub Actions (including orphaned runs)
#
# Prerequisites:
#   - GitHub CLI (gh) installed: brew install gh
#   - Authenticated with GitHub: gh auth login
#   - Repository: Fabric-Management/fabric-management-backend
#
# Usage: ./scripts/cleanup-github-actions.sh [--dry-run]
#
# Last Updated: 2025-01-27

set -euo pipefail

# Colors
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Configuration
readonly REPO="Fabric-Management/fabric-management-backend"
readonly DRY_RUN="${1:-}"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  GitHub Actions Cleanup${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh) is not installed.${NC}"
    echo -e "${YELLOW}Install it with: brew install gh${NC}"
    echo -e "${YELLOW}Then authenticate: gh auth login${NC}"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ Not authenticated with GitHub CLI.${NC}"
    echo -e "${YELLOW}Run: gh auth login${NC}"
    exit 1
fi

# Check if dry-run mode
if [ "${DRY_RUN}" = "--dry-run" ]; then
    echo -e "${YELLOW}🔍 DRY-RUN MODE: No actions will be taken${NC}"
    echo ""
fi

# Get ALL workflow runs (including orphaned ones from deleted workflows)
echo -e "${BLUE}📋 Fetching all workflow runs...${NC}"

# GitHub API returns max 100 per page, we need to paginate
PAGE=1
PER_PAGE=100
ALL_RUN_IDS=""

while true; do
    RUNS_PAGE=$(gh api "repos/${REPO}/actions/runs?per_page=${PER_PAGE}&page=${PAGE}" --jq '.workflow_runs[] | "\(.id)|\(.name)|\(.workflow_id)|\(.status)|\(.conclusion)|\(.created_at)|\(.head_branch)"' 2>/dev/null || echo "")
    
    if [ -z "$RUNS_PAGE" ]; then
        break
    fi
    
    RUN_COUNT=$(echo "$RUNS_PAGE" | grep -c . || echo "0")
    if [ "$RUN_COUNT" -eq 0 ]; then
        break
    fi
    
    ALL_RUN_IDS="${ALL_RUN_IDS}${RUNS_PAGE}"$'\n'
    echo -e "${BLUE}  📄 Page ${PAGE}: ${RUN_COUNT} run(s)${NC}"
    
    # If we got less than PER_PAGE, we're on the last page
    if [ "$RUN_COUNT" -lt "$PER_PAGE" ]; then
        break
    fi
    
    PAGE=$((PAGE + 1))
done

# Remove empty lines
ALL_RUN_IDS=$(echo "$ALL_RUN_IDS" | grep -v '^$' || echo "")

if [ -z "$ALL_RUN_IDS" ]; then
    echo -e "${GREEN}✅ No workflow runs found${NC}"
    exit 0
fi

TOTAL_RUNS=$(echo "$ALL_RUN_IDS" | grep -c . || echo "0")
echo -e "${GREEN}✅ Found ${TOTAL_RUNS} total run(s)${NC}"
echo ""

# Group runs by workflow name for better display
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Workflow Runs Summary${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Group by workflow name
WORKFLOW_GROUPS=$(echo "$ALL_RUN_IDS" | awk -F'|' '{print $2}' | sort | uniq -c | sort -rn)

while IFS=' ' read -r COUNT WORKFLOW_NAME; do
    if [ -n "$COUNT" ] && [ -n "$WORKFLOW_NAME" ]; then
        echo -e "${YELLOW}  ${WORKFLOW_NAME}: ${COUNT} run(s)${NC}"
    fi
done <<< "$WORKFLOW_GROUPS"

echo ""

# Show orphaned runs (workflow_id is null or empty)
ORPHANED_COUNT=$(echo "$ALL_RUN_IDS" | awk -F'|' '$3 == "" || $3 == "null" {print}' | grep -c . || echo "0")
if [ "$ORPHANED_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}  ⚠️  Orphaned runs (from deleted workflows): ${ORPHANED_COUNT}${NC}"
fi

echo ""

# Process runs
if [ "${DRY_RUN}" = "--dry-run" ]; then
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Preview: Runs to be deleted${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    while IFS='|' read -r RUN_ID WORKFLOW_NAME WORKFLOW_ID STATUS CONCLUSION CREATED_AT BRANCH; do
        if [ -z "$RUN_ID" ]; then
            continue
        fi
        
        ORPHANED_MARK=""
        if [ -z "$WORKFLOW_ID" ] || [ "$WORKFLOW_ID" = "null" ]; then
            ORPHANED_MARK=" ${RED}[ORPHANED]${NC}"
        fi
        
        echo -e "  - Run #${RUN_ID} | ${WORKFLOW_NAME}${ORPHANED_MARK}"
        echo -e "    Branch: ${BRANCH} | Status: ${STATUS} | ${CREATED_AT}"
    done <<< "$ALL_RUN_IDS"
    
    echo ""
else
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Deleting workflow runs...${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    TOTAL_DELETED=0
    TOTAL_FAILED=0
    
    while IFS='|' read -r RUN_ID WORKFLOW_NAME WORKFLOW_ID STATUS CONCLUSION CREATED_AT BRANCH; do
        if [ -z "$RUN_ID" ]; then
            continue
        fi
        
        ORPHANED_MARK=""
        if [ -z "$WORKFLOW_ID" ] || [ "$WORKFLOW_ID" = "null" ]; then
            ORPHANED_MARK=" [ORPHANED]"
        fi
        
        if gh api -X DELETE "repos/${REPO}/actions/runs/${RUN_ID}" &> /dev/null; then
            echo -e "  ${GREEN}✅${NC} Deleted run #${RUN_ID} | ${WORKFLOW_NAME}${ORPHANED_MARK} | ${BRANCH}"
            TOTAL_DELETED=$((TOTAL_DELETED + 1))
        else
            echo -e "  ${RED}❌${NC} Failed to delete run #${RUN_ID} | ${WORKFLOW_NAME}"
            TOTAL_FAILED=$((TOTAL_FAILED + 1))
        fi
    done <<< "$ALL_RUN_IDS"
    
    echo ""
fi

# Summary
echo -e "${BLUE}========================================${NC}"
if [ "${DRY_RUN}" = "--dry-run" ]; then
    echo -e "${YELLOW}🔍 DRY-RUN Complete${NC}"
    echo -e "${YELLOW}Found ${TOTAL_RUNS} run(s) to delete${NC}"
    echo -e "${YELLOW}Run without --dry-run to actually delete runs${NC}"
else
    echo -e "${GREEN}✅ Cleanup Complete!${NC}"
    echo -e "${GREEN}   Total runs found: ${TOTAL_RUNS}${NC}"
    echo -e "${GREEN}   Deleted: ${TOTAL_DELETED} run(s)${NC}"
    if [ $TOTAL_FAILED -gt 0 ]; then
        echo -e "${RED}   Failed: ${TOTAL_FAILED} run(s)${NC}"
    fi
fi
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${BLUE}💡 View cleaned workflows:${NC}"
echo -e "   https://github.com/${REPO}/actions"
echo ""
