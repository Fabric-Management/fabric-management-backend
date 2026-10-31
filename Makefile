# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE
# =============================================================================

.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.PHONY: help setup build compile check check-run run format lint test verify verify-coverage build-4-verify up down logs status db-migrate db-repair db-repair-checksum db-reset db-shell clean info

.DEFAULT_GOAL := help

# --- Variables ---
APP_PORT ?= 8080
BASE_URL := http://localhost:$(APP_PORT)
POSTGRES_SERVICE := postgres
POSTGRES_CONTAINER := fabric-postgres
# Seconds `make check-run` waits for the stack to report healthy.
CHECK_RUN_TIMEOUT ?= 300
# psql: use PATH, or common macOS locations. Override: make db-repair-checksum PSQL=/path/to/psql
PSQL ?= $(shell which psql 2>/dev/null || ( [ -x /usr/local/opt/postgresql@16/bin/psql ] && echo /usr/local/opt/postgresql@16/bin/psql ) || ( [ -x /usr/local/opt/postgresql/bin/psql ] && echo /usr/local/opt/postgresql/bin/psql ) || ( [ -x /opt/homebrew/opt/postgresql@16/bin/psql ] && echo /opt/homebrew/opt/postgresql@16/bin/psql ) || ( [ -x /opt/homebrew/opt/postgresql/bin/psql ] && echo /opt/homebrew/opt/postgresql/bin/psql ) || ( [ -x /Applications/Postgres.app/Contents/Versions/latest/bin/psql ] && echo /Applications/Postgres.app/Contents/Versions/latest/bin/psql ) || echo psql)
MVN := $(if $(wildcard mvnw),./mvnw,mvn)
BUILD_4_BASELINE ?= $(HOME)/build-4-baseline
BUILD_4_AFTER ?= $(HOME)/build-4-after
BUILD_4_EVIDENCE ?= $(abspath ../docs/platform/evidence/build-4/after.md)
BUILD_4_EXPLANATIONS ?= $(abspath ../docs/platform/evidence/build-4/expected-deltas.json)
# Auto-detect JAVA_HOME for Homebrew OpenJDK when not set (prefer Java 21 for project compatibility)
export JAVA_HOME ?= $(or $(shell [ -d /usr/local/opt/openjdk@21 ] && echo /usr/local/opt/openjdk@21),$(shell [ -d /opt/homebrew/opt/openjdk@21 ] && echo /opt/homebrew/opt/openjdk@21),$(shell [ -d /usr/local/opt/openjdk ] && echo /usr/local/opt/openjdk),$(shell [ -d /opt/homebrew/opt/openjdk ] && echo /opt/homebrew/opt/openjdk))

# Colors
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
RED := \033[0;31m
NC := \033[0m

# =============================================================================
# 📖 HELP
# =============================================================================
help: ## Show available commands
	@echo "$(BLUE)======================================================================$(NC)"
	@echo "$(GREEN)  🚀 FABRIC MANAGEMENT - MAKEFILE COMMANDS$(NC)"
	@echo "$(BLUE)======================================================================$(NC)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf ""} \
		/^[a-zA-Z_-]+:.*?##/ { printf "  $(YELLOW)make %-15s$(NC) %s\n", $$1, $$2 } \
		/^##@/ { printf "\n$(BLUE)%s$(NC)\n", substr($$0, 5) } ' $(MAKEFILE_LIST)
	@echo ""

##@ 📦 APPLICATION (BUILD & RUN)
setup: ## Initial setup (create .env, setup git hooks)
	@echo "$(YELLOW)🔧 Setting up environment...$(NC)"
	@cp -n .env.example .env 2>/dev/null || true
	@./scripts/setup-git-hooks.sh 2>/dev/null || true
	@echo "$(GREEN)✅ Setup completed.$(NC)"

build: ## Build the application (skips tests)
	@echo "$(YELLOW)🏗️  Building application...$(NC)"
	$(MVN) clean package -DskipTests
	@echo "$(GREEN)✅ Build completed.$(NC)"

compile: ## Compile only (fast; catches compile errors)
	@echo "$(YELLOW)🔨 Compiling...$(NC)"
	$(MVN) compile -q
	@echo "$(GREEN)✅ Compile OK.$(NC)"

check: compile test ## Compile + unit tests (no app start). For startup verification use check-run.
	@echo "$(GREEN)✅ Check passed. Start the stack with 'make run'.$(NC)"

# The application runs only in Docker (docker profile, port APP_PORT). The image's HEALTHCHECK
# calls /actuator/health, so --wait fails when the app does not come up: bean wiring, Flyway and
# circular-dependency errors surface here, as the retired local-profile check-run used to show.
check-run: check ## Build the image, start the stack and wait until the app reports healthy.
	@echo "$(YELLOW)🔍 Verifying application startup (Docker)...$(NC)"
	docker compose up -d --build --wait --wait-timeout $(CHECK_RUN_TIMEOUT)
	@echo "$(GREEN)✅ Check-run passed. The app is healthy at $(BASE_URL).$(NC)"

run: ## Build and start the application in Docker (the only way to run it locally).
	@echo "$(YELLOW)🚀 Starting the application (Docker)...$(NC)"
	docker compose up -d --build
	@echo "$(GREEN)✅ Starting at $(BASE_URL); follow it with 'make logs'.$(NC)"

##@ 💅 CODE QUALITY & TESTS
format: ## Format code (Google Java Format)
	@echo "$(YELLOW)💅 Formatting code...$(NC)"
	$(MVN) fmt:format
	@echo "$(GREEN)✅ Code formatted.$(NC)"

lint: ## Check code quality (format, checkstyle, spotbugs) - Fails on violation
	@echo "$(YELLOW)🔍 Checking code quality...$(NC)"
	$(MVN) fmt:check checkstyle:check spotbugs:check
	@echo "$(GREEN)✅ Code quality is good.$(NC)"

test: ## Run fast unit tests
	@echo "$(YELLOW)🧪 Running unit tests...$(NC)"
	$(MVN) test
	@echo "$(GREEN)✅ Unit tests passed.$(NC)"

verify: ## Run all tests and generate coverage report (no coverage gate)
	@echo "$(YELLOW)🧪 Running all tests and generating coverage...$(NC)"
	$(MVN) verify
	@echo "$(GREEN)✅ All tests passed. Report: target/site/jacoco/index.html$(NC)"

verify-coverage: ## Run all tests and enforce the ratcheted line-coverage baseline
	@echo "$(YELLOW)🧪 Running tests with coverage gate...$(NC)"
	$(MVN) verify -Pcoverage
	@echo "$(GREEN)✅ Tests and coverage check passed.$(NC)"

build-4-verify: ## Run and archive the complete BUILD-4 PostgreSQL verification
	./scripts/build_4_verify.sh \
		"$(BUILD_4_BASELINE)" \
		"$(BUILD_4_AFTER)" \
		"$(BUILD_4_EVIDENCE)" \
		"$(BUILD_4_EXPLANATIONS)"

##@ 🐳 DOCKER INFRASTRUCTURE
up: ## Start all required infrastructure (PostgreSQL, etc.)
	@echo "$(YELLOW)🚀 Starting infrastructure...$(NC)"
	docker compose up -d
	@sleep 3
	@docker compose ps

down: ## Stop all infrastructure
	@echo "$(YELLOW)🛑 Stopping infrastructure...$(NC)"
	docker compose down
	@echo "$(GREEN)✅ Infrastructure stopped.$(NC)"

logs: ## Tail logs from all Docker services
	docker compose logs -f --tail=100

status: ## Show Docker service status
	@echo "$(YELLOW)📊 Service Status:$(NC)"
	docker compose ps

##@ 🗄️ DATABASE
db-migrate: ## Run Flyway database migrations
	@echo "$(YELLOW)🗄️  Running database migrations...$(NC)"
	$(MVN) flyway:migrate
	@echo "$(GREEN)✅ Migrations completed.$(NC)"

# DB connection for repair: the compose PostgreSQL on the host (match flyway.conf)
REPAIR_DB_HOST ?= localhost
REPAIR_DB_PORT ?= 5433
REPAIR_DB_USER ?= fabric_owner
REPAIR_DB_NAME ?= fabric_management
REPAIR_DB_PASSWORD ?= owner_dev_2026

db-repair: ## Fix Flyway checksum mismatches (when migration files were edited after apply). Then run 'make run'.
	@echo "$(YELLOW)🔧 Repairing Flyway schema history (updating checksums)...$(NC)"
	@( $(MVN) flyway:repair -q 2>/dev/null ) || ( echo "$(YELLOW)Maven repair failed (e.g. duplicate versions). Trying SQL repair...$(NC)"; $(MAKE) db-repair-checksum )

db-repair-checksum: ## Fix checksum for V20250314190000 via SQL (run if make db-repair fails). Requires psql.
	@echo "$(YELLOW)🔧 Updating Flyway checksum for V20250314190000...$(NC)"
	@PGPASSWORD=$(REPAIR_DB_PASSWORD) $(PSQL) -h $(REPAIR_DB_HOST) -p $(REPAIR_DB_PORT) -U $(REPAIR_DB_USER) -d $(REPAIR_DB_NAME) -f "$(CURDIR)/scripts/flyway-repair-checksum.sql"
	@echo "$(GREEN)✅ Checksum repaired. Start app with: make run$(NC)"

db-reset: ## Reset database volume completely (Docker) - DESTRUCTIVE!
	@echo "$(RED)⚠️  Resetting database volume...$(NC)"
	docker compose down $(POSTGRES_SERVICE)
	docker volume rm fabric-management-backend_postgres_data 2>/dev/null || true
	docker compose up -d $(POSTGRES_SERVICE)
	@echo "$(GREEN)✅ DB reset complete. Flyway will run on next app start.$(NC)"

db-shell: ## Open PostgreSQL shell
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_owner -d fabric_management

##@ 🧹 CLEANUP & UTILS
clean: ## Clean Maven build and remove Docker volumes (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Cleaning project and infrastructure...$(NC)"
	$(MVN) clean
	docker compose down -v 2>/dev/null || true
	@echo "$(GREEN)✅ Clean completed.$(NC)"

info: ## Show system information (Java, Maven, Docker)
	@echo "$(BLUE)📋 System Information:$(NC)"
	@java -version 2>&1 | head -1
	@$(MVN) -version | head -1
	@docker --version
	@docker compose version
