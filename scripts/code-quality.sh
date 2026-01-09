#!/bin/bash
# =============================================================================
# CODE QUALITY ANALYSIS SCRIPT
# =============================================================================
# Runs all code quality checks: Format → Checkstyle → SpotBugs
#
# Usage: ./scripts/code-quality.sh
# Last Updated: 2025-01-27

set -euo pipefail

# Colors
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Code Quality Analysis${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Format code
echo -e "${YELLOW}1️⃣  Formatting code (Google Java Format)...${NC}"
if mvn fmt:format; then
    echo -e "${GREEN}✅ Code formatted${NC}"
else
    echo -e "${RED}❌ Formatting failed${NC}"
    exit 1
fi
echo ""

# Step 2: Checkstyle
echo -e "${YELLOW}2️⃣  Running Checkstyle (Google Style)...${NC}"
if mvn checkstyle:check; then
    echo -e "${GREEN}✅ Checkstyle passed${NC}"
else
    echo -e "${YELLOW}⚠️  Checkstyle found issues (check target/checkstyle-result.xml)${NC}"
fi
echo ""

# Step 3: SpotBugs
echo -e "${YELLOW}3️⃣  Running SpotBugs (bug detection)...${NC}"
if mvn spotbugs:check; then
    echo -e "${GREEN}✅ SpotBugs passed${NC}"
else
    echo -e "${YELLOW}⚠️  SpotBugs found issues (check target/spotbugsXml.xml)${NC}"
fi
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Code Quality Analysis Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}📊 Reports:${NC}"
echo "  - Checkstyle: target/checkstyle-result.xml"
echo "  - SpotBugs:   target/spotbugsXml.xml"
echo ""
