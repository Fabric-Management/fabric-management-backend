# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE
# =============================================================================

.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.PHONY: help setup build compile check check-run run format lint test verify verify-coverage up down logs status db-migrate db-repair db-repair-checksum db-reset db-reset-local db-shell clean dev-reset info

.DEFAULT_GOAL := help

# --- Variables ---
APP_PORT ?= 8080
BASE_URL := http://localhost:$(APP_PORT)
POSTGRES_SERVICE := postgres
POSTGRES_CONTAINER := fabric-postgres
POSTGRES_LOCAL_USER ?= postgres
# psql: use PATH, or common macOS locations. Override: make db-reset-local PSQL=/path/to/psql
PSQL ?= $(shell which psql 2>/dev/null || ( [ -x /usr/local/opt/postgresql@16/bin/psql ] && echo /usr/local/opt/postgresql@16/bin/psql ) || ( [ -x /usr/local/opt/postgresql/bin/psql ] && echo /usr/local/opt/postgresql/bin/psql ) || ( [ -x /opt/homebrew/opt/postgresql@16/bin/psql ] && echo /opt/homebrew/opt/postgresql@16/bin/psql ) || ( [ -x /opt/homebrew/opt/postgresql/bin/psql ] && echo /opt/homebrew/opt/postgresql/bin/psql ) || ( [ -x /Applications/Postgres.app/Contents/Versions/latest/bin/psql ] && echo /Applications/Postgres.app/Contents/Versions/latest/bin/psql ) || echo psql)
MVN := $(if $(wildcard mvnw),./mvnw,mvn)
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

check: compile test ## Compile + unit tests (no Docker). For startup verification use check-run.
	@echo "$(GREEN)✅ Check passed. Safe to run 'make run'.$(NC)"

check-run: check ## Verify app starts with local PostgreSQL (no Docker). Catches circular deps, Flyway, bean errors.
	@echo "$(YELLOW)🔍 Verifying application startup (local DB)...$(NC)"
	@chmod +x "$(CURDIR)/scripts/check-run.sh"
	@$(CURDIR)/scripts/check-run.sh
	@echo "$(GREEN)✅ Check-run passed. App starts successfully.$(NC)"

run: ## Run the Spring Boot application (local profile). Tip: run 'make check-run' before to catch startup errors.
	@echo "$(YELLOW)🚀 Running application (local profile)...$(NC)"
	$(MVN) spring-boot:run -Dspring-boot.run.profiles=local

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

verify-coverage: ## Run tests and enforce 80%% line coverage (e.g. for CI)
	@echo "$(YELLOW)🧪 Running tests with coverage gate...$(NC)"
	$(MVN) verify -Pcoverage
	@echo "$(GREEN)✅ Tests and coverage check passed.$(NC)"

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

# DB connection for repair (match flyway.conf / application-local)
REPAIR_DB_HOST ?= localhost
REPAIR_DB_PORT ?= 5432
REPAIR_DB_USER ?= fabric_user
REPAIR_DB_NAME ?= fabric_management
REPAIR_DB_PASSWORD ?= local_dev_2026

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

db-reset-local: ## Reset local PostgreSQL DB (drop + recreate fabric_management). Uses POSTGRES_LOCAL_USER (default: postgres). On Homebrew use: POSTGRES_LOCAL_USER=$(whoami)
	@echo "$(RED)⚠️  Resetting local database fabric_management...$(NC)"
	@$(PSQL) -U $(POSTGRES_LOCAL_USER) -d postgres -f "$(CURDIR)/scripts/db-reset-local.sql"
	@echo "$(GREEN)✅ DB reset complete. Run: make run$(NC)"

db-shell: ## Open PostgreSQL shell
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management

##@ 🧹 CLEANUP & UTILS
clean: ## Clean Maven build and remove Docker volumes (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Cleaning project and infrastructure...$(NC)"
	$(MVN) clean
	docker compose down -v 2>/dev/null || true
	@echo "$(GREEN)✅ Clean completed.$(NC)"

dev-reset: ## Reset ALL application data via API (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Resetting all data via API...$(NC)"
	curl -s -X POST $(BASE_URL)/api/dev/reset-all | jq . 2>/dev/null || echo "$(RED)❌ API not responding$(NC)"

info: ## Show system information (Java, Maven, Docker)
	@echo "$(BLUE)📋 System Information:$(NC)"
	@java -version 2>&1 | head -1
	@$(MVN) -version | head -1
	@docker --version
	@docker compose version
