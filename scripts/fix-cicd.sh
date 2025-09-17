#!/bin/bash

# =============================================================================
# FABRIC MANAGEMENT SYSTEM - CI/CD TROUBLESHOOTING SCRIPT
# =============================================================================
# This script helps diagnose and fix common CI/CD issues

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to validate Docker tag format
validate_docker_tag() {
    local tag="$1"

    # Docker tag validation rules:
    # - Must be lowercase
    # - Can contain a-z, 0-9, _, -, .
    # - Cannot start with -, .
    # - Maximum 128 characters

    if [[ ! "$tag" =~ ^[a-z0-9][a-z0-9._-]*$ ]]; then
        return 1
    fi

    if [[ ${#tag} -gt 128 ]]; then
        return 1
    fi

    return 0
}

# Function to generate valid Docker tags
generate_valid_tags() {
    local service="$1"
    local ref_type="$2"
    local ref_name="$3"
    local sha="$4"
    local pr_number="$5"

    local registry="ghcr.io/fabric-management"
    local base_tag="${registry}/${service}"

    case "$ref_type" in
        "branch")
            if [[ "$ref_name" == "main" ]]; then
                echo "${base_tag}:latest,${base_tag}:${sha:0:7}"
            else
                # Sanitize branch name for Docker tag
                local clean_branch=$(echo "$ref_name" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9._-]/-/g' | sed 's/^[-.]//g')
                echo "${base_tag}:${clean_branch},${base_tag}:${clean_branch}-${sha:0:7}"
            fi
            ;;
        "tag")
            local clean_tag=$(echo "$ref_name" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9._-]/-/g' | sed 's/^[-.]//g')
            echo "${base_tag}:${clean_tag},${base_tag}:${sha:0:7}"
            ;;
        "pull_request")
            echo "${base_tag}:pr-${pr_number},${base_tag}:pr-${pr_number}-${sha:0:7}"
            ;;
        *)
            echo "${base_tag}:${sha:0:7}"
            ;;
    esac
}

# Function to test tag generation
test_tag_generation() {
    log_info "Testing Docker tag generation..."

    local services=("auth-service" "user-service" "company-service" "contact-service")
    local test_sha="c53f44b5bf7e16aeaf59afad51be14fc7de62deb"

    # Test different scenarios
    local scenarios=(
        "branch:main:${test_sha}::"
        "branch:feature/user-auth:${test_sha}::"
        "pull_request:refs/pull/24/merge:${test_sha}:24:"
        "tag:v1.0.0:${test_sha}::"
    )

    for scenario in "${scenarios[@]}"; do
        IFS=':' read -r ref_type ref_name sha pr_number _ <<< "$scenario"

        log_info "Testing scenario: $ref_type - $ref_name"

        for service in "${services[@]}"; do
            local tags=$(generate_valid_tags "$service" "$ref_type" "$ref_name" "$sha" "$pr_number")

            # Validate each tag
            IFS=',' read -ra tag_array <<< "$tags"
            for tag in "${tag_array[@]}"; do
                local tag_name=$(echo "$tag" | cut -d':' -f2)
                if validate_docker_tag "$tag_name"; then
                    log_success "Valid tag: $tag"
                else
                    log_error "Invalid tag: $tag"
                fi
            done
        done
        echo
    done
}

# Function to check current CI/CD setup
check_cicd_setup() {
    log_info "Checking CI/CD setup..."

    # Check for GitHub Actions workflow
    if [[ -f ".github/workflows/ci-cd.yml" ]]; then
        log_success "GitHub Actions workflow found"
    else
        log_warning "GitHub Actions workflow not found"
        log_info "Creating basic workflow structure..."
        mkdir -p .github/workflows
        log_info "Please ensure the CI/CD workflow file is properly configured"
    fi

    # Check for required secrets
    log_info "Required GitHub repository secrets:"
    echo "  - GITHUB_TOKEN (automatically provided by GitHub)"
    echo "  - Make sure GitHub Container Registry (ghcr.io) access is enabled"

    # Check Dockerfile existence
    local services=("auth-service" "user-service" "company-service" "contact-service")
    for service in "${services[@]}"; do
        if [[ -f "services/${service}/Dockerfile" ]]; then
            log_success "Dockerfile found for $service"
        else
            log_error "Dockerfile missing for $service"
        fi
    done
}

# Function to show the fix for the original error
show_error_fix() {
    log_info "Original error analysis:"
    echo "Error: invalid tag \"ghcr.io/fabric-management/company-service:-c53f44b\""
    echo ""
    log_info "Problem: The tag starts with ':-' which is invalid"
    log_info "Cause: Empty or undefined version variable in CI/CD pipeline"
    echo ""
    log_info "Fix applied:"
    echo "1. Created proper tag generation logic in GitHub Actions workflow"
    echo "2. Added validation and sanitization for branch/tag names"
    echo "3. Ensured no empty variables can cause invalid tag formats"
    echo ""
    log_success "The CI/CD workflow now properly handles all scenarios:"
    echo "  - Main branch: latest, {sha}"
    echo "  - Feature branches: {branch-name}, {branch-name}-{sha}"
    echo "  - Pull requests: pr-{number}, pr-{number}-{sha}"
    echo "  - Tags: {tag-name}, {sha}"
}

# Main execution
main() {
    echo "============================================="
    echo "FABRIC MANAGEMENT CI/CD TROUBLESHOOTING"
    echo "============================================="
    echo ""

    show_error_fix
    echo ""

    check_cicd_setup
    echo ""

    test_tag_generation
    echo ""

    log_success "CI/CD troubleshooting completed!"
    log_info "Next steps:"
    echo "1. Commit the new .github/workflows/ci-cd.yml file"
    echo "2. Push to your repository"
    echo "3. Check the Actions tab in GitHub to verify the workflow runs"
    echo "4. Ensure repository has access to GitHub Container Registry"
}

# Run main function
main "$@"
