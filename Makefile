# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE (Modular Monolith) - CLEANED
# =============================================================================
# Production-ready development & deployment commands (consistent & deduplicated)

.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.PHONY: help setup validate-env app-build app-run test test-integration coverage \
        dev up up-all down down-clean restart restart-db status ps logs logs-db logs-redis logs-errors \
        health metrics prometheus grafana swagger \
        db-shell db-migrate db-info db-validate db-clean db-tables db-schemas \
        db-view-companies db-view-users db-view-subscriptions db-view-tokens db-view-all \
        show-tables \
        db-backup db-restore db-reset \
        kafka-topics kafka-describe kafka-consumer \
        clean clean-docker prune rebuild \
        lint format \
        quick-test full-cycle dev-workflow dev-reset dev-clean-tokens dev-clean-codes dev-stats dev-tools-health \
        git-status git-branch git-diff info endpoints

.DEFAULT_GOAL := help

# =============================================================================
# CONFIG
# =============================================================================
# Colors
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m

# Ports & URLs
APP_PORT ?= 8080
PROM_PORT ?= 9090
GRAFANA_PORT ?= 3000
BASE_URL := http://localhost:$(APP_PORT)

# Containers & services (Docker Compose service names)
POSTGRES_SERVICE := postgres
REDIS_SERVICE := redis
KAFKA_SERVICE := kafka
PROM_SERVICE := prometheus
GRAFANA_SERVICE := grafana

# Explicit container names if set in docker-compose
POSTGRES_CONTAINER := fabric-postgres
KAFKA_CONTAINER := fabric-kafka

# =============================================================================
# HELP
# =============================================================================
help: ## Show available commands
	@echo "$(GREEN)============================================$(NC)"
	@echo "$(GREEN)  Fabric Management - Modular Monolith$(NC)"
	@echo "$(GREEN)============================================$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z0-9._-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort \
	| awk 'BEGIN {FS = ":.*?## "}; {printf "$(BLUE)%-25s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(YELLOW)Quick Start:$(NC)"
	@echo "  make dev          # Start PostgreSQL + Redis"
	@echo "  make app-run      # Run Spring Boot app"
	@echo "  make health       # Check application health"

# =============================================================================
# SETUP
# =============================================================================
setup: ## Initial setup - create .env from template
	@echo "$(YELLOW)🔧 Setting up environment...$(NC)"
	if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo "$(GREEN)✅ .env created. Update with your values.$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  .env already exists.$(NC)"; \
	fi

validate-env: ## Validate .env file exists
	@echo "$(YELLOW)🔍 Validating environment...$(NC)"
	if [ ! -f .env ]; then \
		echo "$(RED)❌ .env not found! Run: make setup$(NC)"; exit 1; \
	fi
	@echo "$(GREEN)✅ Environment valid$(NC)"

# =============================================================================
# APPLICATION BUILD & TEST
# =============================================================================
app-build: ## Build Spring Boot application (Maven)
	@echo "$(YELLOW)🏗️  Building application...$(NC)"
	mvn clean package -DskipTests
	@echo "$(GREEN)✅ Built → target/fabric-management-backend-1.0.0-SNAPSHOT.jar$(NC)"

app-run: ## Run Spring Boot application (local profile)
	@echo "$(YELLOW)🚀 Running application...$(NC)"
	mvn spring-boot:run -Dspring-boot.run.profiles=local

test: ## Run unit tests
	@echo "$(YELLOW)🧪 Running unit tests...$(NC)"
	mvn test
	@echo "$(GREEN)✅ Unit tests completed$(NC)"

test-integration: ## Run integration tests
	@echo "$(YELLOW)🧪 Running integration tests...$(NC)"
	mvn verify
	@echo "$(GREEN)✅ Integration tests completed$(NC)"

coverage: ## Generate test coverage report (JaCoCo)
	@echo "$(YELLOW)📊 Generating coverage report...$(NC)"
	mvn jacoco:report
	@echo "$(GREEN)✅ Coverage: target/site/jacoco/index.html$(NC)"

lint: ## Check code quality (verify without tests)
	@echo "$(YELLOW)🔍 Checking code quality...$(NC)"
	mvn verify -DskipTests
	@echo "$(GREEN)✅ Code quality check completed$(NC)"

format: ## Format code (Spotless if configured)
	@echo "$(YELLOW)💅 Formatting code...$(NC)"
	mvn spotless:apply 2>/dev/null || echo "$(YELLOW)⚠️  Spotless not configured$(NC)"
	@echo "$(GREEN)✅ Code formatted$(NC)"

# =============================================================================
# DOCKER INFRASTRUCTURE
# =============================================================================
dev: validate-env ## Start PostgreSQL + Redis (fast dev mode)
	@echo "$(YELLOW)🚀 Starting development infra (Postgres + Redis)...$(NC)"
	docker compose up -d $(POSTGRES_SERVICE) $(REDIS_SERVICE)
	@sleep 3
	@$(MAKE) status

up: validate-env ## Start core + Kafka + Monitoring (commonly used)
	@echo "$(YELLOW)🚀 Starting core infra (Postgres, Redis, Kafka, Prometheus, Grafana)...$(NC)"
	docker compose up -d $(POSTGRES_SERVICE) $(REDIS_SERVICE) $(KAFKA_SERVICE) $(PROM_SERVICE) $(GRAFANA_SERVICE)
	@sleep 5
	@$(MAKE) status

up-all: validate-env ## Start everything defined in docker-compose
	@echo "$(YELLOW)🚀 Starting ALL services...$(NC)"
	docker compose up -d
	@sleep 8
	@$(MAKE) status

down: ## Stop all Docker services
	@echo "$(YELLOW)🛑 Stopping services...$(NC)"
	docker compose down
	@echo "$(GREEN)✅ Services stopped$(NC)"

down-clean: ## Stop services + remove volumes (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Stopping & removing volumes...$(NC)"
	docker compose down -v
	@echo "$(GREEN)✅ Services stopped, volumes removed$(NC)"

restart: ## Restart all Docker services
	@echo "$(YELLOW)🔄 Restarting services...$(NC)"
	docker compose restart
	@echo "$(GREEN)✅ Services restarted$(NC)"

restart-db: ## Restart PostgreSQL only
	@echo "$(YELLOW)🔄 Restarting PostgreSQL...$(NC)"
	docker compose restart $(POSTGRES_SERVICE)
	@echo "$(GREEN)✅ PostgreSQL restarted$(NC)"

status: ## Show Docker service status
	@echo "$(YELLOW)📊 Service Status:$(NC)"
	docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

ps: ## Show running containers (fabric-*)
	docker ps --filter "name=fabric-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

logs: ## Tail logs from all services
	docker compose logs -f --tail=100

logs-db: ## Tail PostgreSQL logs
	docker compose logs -f --tail=100 $(POSTGRES_SERVICE)

logs-redis: ## Tail Redis logs
	docker compose logs -f --tail=100 $(REDIS_SERVICE)

logs-errors: ## Grep ERROR/EXCEPTION in last 200 lines
	docker compose logs --tail=200 | grep -iE "error|exception|failed" || echo "$(GREEN)✅ No errors$(NC)"

# =============================================================================
# HEALTH & MONITORING
# =============================================================================
health: ## Check application + infra health
	@echo "$(BLUE)🏥 Health Check:$(NC)"
	echo "\n$(YELLOW)Application Health:$(NC)"
	curl -s $(BASE_URL)/api/health | jq . 2>/dev/null || echo "$(RED)❌ Application not responding$(NC)"
	echo "\n$(YELLOW)Actuator Health:$(NC)"
	curl -s $(BASE_URL)/actuator/health | jq . 2>/dev/null || echo "$(RED)❌ Actuator not responding$(NC)"
	echo "\n$(YELLOW)PostgreSQL:$(NC)"
	docker compose ps $(POSTGRES_SERVICE) | grep -q "(healthy)" && echo "$(GREEN)✅ Healthy$(NC)" || echo "$(RED)❌ Unhealthy$(NC)"
	echo "\n$(YELLOW)Redis:$(NC)"
	docker compose ps $(REDIS_SERVICE) | grep -q "(healthy)" && echo "$(GREEN)✅ Healthy$(NC)" || echo "$(RED)❌ Unhealthy$(NC)"

metrics: ## Show actuator metrics index
	@echo "$(YELLOW)📊 Application Metrics:$(NC)"
	curl -s $(BASE_URL)/actuator/metrics | jq . 2>/dev/null || echo "$(RED)❌ Metrics not available$(NC)"

prometheus: ## Open Prometheus UI
	@echo "$(BLUE)📊 Opening Prometheus...$(NC)"
	open http://localhost:$(PROM_PORT) || xdg-open http://localhost:$(PROM_PORT) 2>/dev/null || echo "Visit: http://localhost:$(PROM_PORT)"

grafana: ## Open Grafana UI
	@echo "$(BLUE)📊 Opening Grafana...$(NC)"
	open http://localhost:$(GRAFANA_PORT) || xdg-open http://localhost:$(GRAFANA_PORT) 2>/dev/null || echo "Visit: http://localhost:$(GRAFANA_PORT) (admin/admin)"

swagger: ## Open Swagger UI
	@echo "$(BLUE)📖 Opening Swagger UI...$(NC)"
	open $(BASE_URL)/swagger-ui.html || xdg-open $(BASE_URL)/swagger-ui.html 2>/dev/null || echo "Visit: $(BASE_URL)/swagger-ui.html"

# =============================================================================
# DATABASE (PostgreSQL)
# =============================================================================
db-shell: ## Open PostgreSQL shell
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management

db-migrate: ## Run Flyway migrations
	@echo "$(YELLOW)🗄️  Running database migrations...$(NC)"
	mvn flyway:migrate
	@echo "$(GREEN)✅ Migrations completed$(NC)"

db-info: ## Show Flyway migration status
	@echo "$(YELLOW)📊 Migration Status:$(NC)"
	mvn flyway:info

db-validate: ## Validate migrations
	@echo "$(YELLOW)🔍 Validating migrations...$(NC)"
	mvn flyway:validate
	@echo "$(GREEN)✅ Migrations valid$(NC)"

db-clean: ## Clean database (DESTRUCTIVE! Drops all objects)
	@echo "$(RED)⚠️  This will drop ALL DB objects!$(NC)"
	read -p "Are you sure? (yes/no): " confirm; \
	if [ "$$confirm" = "yes" ]; then \
		mvn flyway:clean; \
		echo "$(GREEN)✅ Database cleaned$(NC)"; \
	else \
		echo "$(YELLOW)Cancelled$(NC)"; \
	fi

db-tables: ## List all tables with sizes
	@echo "$(YELLOW)📊 Database Tables:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT schemaname AS schema, tablename AS table, \
	         pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size \
	  FROM pg_tables \
	  WHERE schemaname NOT IN ('pg_catalog','information_schema','flyway') \
	  ORDER BY schemaname, tablename;"

db-schemas: ## List all non-system schemas
	@echo "$(YELLOW)📂 Database Schemas:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT schema_name FROM information_schema.schemata \
	  WHERE schema_name NOT IN ('pg_catalog','information_schema','flyway') \
	  ORDER BY schema_name;"

db-view-companies: ## View company table contents
	@echo "$(BLUE)🏢 Companies:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT id, tenant_id, \
	         CASE WHEN id = tenant_id THEN '✅ ROOT' ELSE '⚠️ BRANCH' END as type, \
	         uid, company_name, company_type, city \
	  FROM common_company.common_company \
	  ORDER BY created_at DESC;"

db-view-users: ## View user table contents
	@echo "$(BLUE)👤 Users:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT u.uid, u.first_name || ' ' || u.last_name as name, u.contact_value, \
	         u.tenant_id, c.company_name, u.department, \
	         CASE WHEN u.onboarding_completed_at IS NULL THEN '⏳ Pending' ELSE '✅ Done' END as onboarding \
	  FROM common_user.common_user u \
	  JOIN common_company.common_company c ON u.company_id = c.id \
	  ORDER BY u.created_at DESC;"

db-view-subscriptions: ## View subscriptions
	@echo "$(BLUE)💳 Subscriptions:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT os_code, os_name, status, pricing_tier, trial_ends_at::date AS trial_ends, c.company_name \
	  FROM common_company.common_subscription s \
	  JOIN common_company.common_company c ON s.tenant_id = c.id \
	  ORDER BY s.created_at;"

db-view-tokens: ## View registration tokens (last 5)
	@echo "$(BLUE)🔑 Registration Tokens:$(NC)"
	docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
	  SELECT token, contact_value, token_type, \
	         CASE WHEN is_used THEN '✅ Used' ELSE '⏳ Pending' END AS status, \
	         expires_at::date AS expires \
	  FROM common_auth.common_registration_token \
	  ORDER BY created_at DESC \
	  LIMIT 5;"

db-view-all: ## View summary tables (companies/users/subscriptions/tokens)
	@$(MAKE) db-view-companies
	@echo ""
	@$(MAKE) db-view-users
	@echo ""
	@$(MAKE) db-view-subscriptions
	@echo ""
	@$(MAKE) db-view-tokens

show-tables: ## Show table contents (make show-tables TABLES="common_role common_department")
	@if [ -z "$(TABLES)" ]; then \
		echo "$(RED)❌ Usage: make show-tables TABLES=\"table1 table2 ...\"$(NC)"; \
		echo "$(YELLOW)Example: make show-tables TABLES=\"common_role common_department_category common_department\"$(NC)"; \
		exit 1; \
	fi
	@for table in $(TABLES); do \
		echo "$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"; \
		echo "$(GREEN)📋 Table: $$table$(NC)"; \
		echo "$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(NC)"; \
		schema=$$(echo $$table | cut -d'.' -f1); \
		tablename=$$(echo $$table | cut -d'.' -f2); \
		if [ "$$tablename" = "$$table" ]; then \
			tablename=$$table; \
			schema=""; \
		fi; \
		if [ -z "$$schema" ]; then \
			docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
				SELECT * FROM $$tablename \
				ORDER BY created_at DESC NULLS LAST \
				LIMIT 50;" 2>/dev/null || \
			docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
				SELECT * FROM common_company.$$tablename \
				ORDER BY created_at DESC NULLS LAST \
				LIMIT 50;" 2>/dev/null || \
			docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
				SELECT * FROM common_user.$$tablename \
				ORDER BY created_at DESC NULLS LAST \
				LIMIT 50;" 2>/dev/null || \
			docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
				SELECT * FROM production.$$tablename \
				ORDER BY created_at DESC NULLS LAST \
				LIMIT 50;" 2>/dev/null || \
			echo "$(RED)❌ Table '$$tablename' not found in common schemas$(NC)"; \
		else \
			docker exec -it $(POSTGRES_CONTAINER) psql -U fabric_user -d fabric_management -c "\
				SELECT * FROM $$schema.$$tablename \
				ORDER BY created_at DESC NULLS LAST \
				LIMIT 50;" 2>/dev/null || \
			echo "$(RED)❌ Table '$$schema.$$tablename' not found$(NC)"; \
		fi; \
		echo ""; \
	done

db-backup: ## Backup database to backups/ folder
	@echo "$(YELLOW)💾 Backing up database...$(NC)"
	mkdir -p backups
	docker exec -t $(POSTGRES_CONTAINER) pg_dump -U fabric_user fabric_management > backups/backup-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)✅ Backup written to backups/$(NC)"

db-restore: ## Restore database (make db-restore FILE=backups/backup.sql)
	@echo "$(YELLOW)📥 Restoring database...$(NC)"
	if [ -z "$(FILE)" ]; then echo "$(RED)❌ Specify: make db-restore FILE=backup.sql$(NC)"; exit 1; fi
	docker exec -i $(POSTGRES_CONTAINER) psql -U fabric_user fabric_management < $(FILE)
	@echo "$(GREEN)✅ Database restored$(NC)"

db-reset: ## Reset DB volume (drop → recreate → up postgres)
	@echo "$(RED)⚠️  Resetting database volume...$(NC)"
	docker compose down $(POSTGRES_SERVICE)
	docker volume rm fabric-management-backend_postgres_data 2>/dev/null || true
	docker compose up -d $(POSTGRES_SERVICE)
	@echo "$(YELLOW)⏳ Waiting for PostgreSQL...$(NC)"; sleep 5
	@echo "$(GREEN)✅ DB reset complete. Flyway will run on next app start.$(NC)"

# =============================================================================
# KAFKA
# =============================================================================
kafka-topics: ## List Kafka topics
	@echo "$(YELLOW)📋 Kafka Topics:$(NC)"
	docker exec $(KAFKA_CONTAINER) kafka-topics --bootstrap-server localhost:9092 --list

kafka-describe: ## Describe Kafka topic (make kafka-describe TOPIC=name)
	@if [ -z "$(TOPIC)" ]; then echo "$(RED)❌ Specify: make kafka-describe TOPIC=topic$(NC)"; exit 1; fi
	docker exec $(KAFKA_CONTAINER) kafka-topics --bootstrap-server localhost:9092 --describe --topic $(TOPIC)

kafka-consumer: ## Consume from topic (make kafka-consumer TOPIC=name)
	@if [ -z "$(TOPIC)" ]; then echo "$(RED)❌ Specify: make kafka-consumer TOPIC=topic$(NC)"; exit 1; fi
	@echo "$(YELLOW)📨 Consuming from: $(TOPIC)$(NC)"
	docker exec -it $(KAFKA_CONTAINER) kafka-console-consumer --bootstrap-server localhost:9092 --topic $(TOPIC) --from-beginning

# =============================================================================
# CLEANUP / REBUILD
# =============================================================================
clean: ## Clean Maven build artifacts
	@echo "$(YELLOW)🧹 Cleaning build artifacts...$(NC)"
	mvn clean
	@echo "$(GREEN)✅ Clean completed$(NC)"

clean-docker: ## Remove Docker images (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Removing Docker images...$(NC)"
	docker compose down --rmi all
	@echo "$(GREEN)✅ Docker images removed$(NC)"

prune: ## Docker system prune
	@echo "$(YELLOW)🧹 Pruning Docker system...$(NC)"
	docker system prune -f
	@echo "$(GREEN)✅ Docker system pruned$(NC)"

rebuild: ## Full rebuild (down-clean → clean → build → up-all)
	@echo "$(YELLOW)🧹 Full clean rebuild...$(NC)"
	$(MAKE) down-clean
	$(MAKE) clean
	$(MAKE) app-build
	$(MAKE) up-all
	@echo "$(GREEN)✅ Rebuild completed$(NC)"

# =============================================================================
# DEV TOOLS (API)
# =============================================================================
dev-reset: ## ⚠️ Reset ALL application data via API
	@echo "$(RED)⚠️  This will DELETE ALL data in DB!$(NC)"
	read -p "Type 'yes' to confirm: " confirm; \
	if [ "$$confirm" = "yes" ]; then \
	  echo "$(YELLOW)🔥 Resetting all data...$(NC)"; \
	  curl -s -X POST $(BASE_URL)/api/dev/reset-all | jq . 2>/dev/null || echo "$(RED)❌ API not responding$(NC)"; \
	  echo "$(GREEN)✅ Data reset complete$(NC)"; \
	else echo "$(YELLOW)Cancelled$(NC)"; fi

dev-clean-tokens: ## Clean expired registration tokens
	@echo "$(YELLOW)🧹 Cleaning expired tokens...$(NC)"
	curl -s -X POST $(BASE_URL)/api/dev/clean-tokens | jq . 2>/dev/null || echo "$(RED)❌ API not responding$(NC)"
	@echo "$(GREEN)✅ Tokens cleaned$(NC)"

dev-clean-codes: ## Clean expired verification codes
	@echo "$(YELLOW)🧹 Cleaning expired verification codes...$(NC)"
	curl -s -X POST $(BASE_URL)/api/dev/clean-codes | jq . 2>/dev/null || echo "$(RED)❌ API not responding$(NC)"
	@echo "$(GREEN)✅ Codes cleaned$(NC)"

dev-stats: ## Show DB statistics via API
	@echo "$(BLUE)📊 Database Statistics:$(NC)"
	curl -s $(BASE_URL)/api/dev/stats | jq . 2>/dev/null || echo "$(RED)❌ API not responding$(NC)"

dev-tools-health: ## Check dev-tools (profile=local)
	@echo "$(BLUE)🔧 Development Tools Status:$(NC)"
	curl -s $(BASE_URL)/api/dev/health | jq . 2>/dev/null || echo "$(RED)❌ Dev tools not available$(NC)"

# =============================================================================
# QUICK FLOWS
# =============================================================================
quick-test: ## Quick test (stats → reset → stats)
	@echo "$(BLUE)🧪 Quick Test Cycle:$(NC)"
	echo ""; echo "$(YELLOW)1️⃣ Current Stats:$(NC)"
	$(MAKE) dev-stats
	echo ""; echo "$(YELLOW)2️⃣ Resetting data...$(NC)"
	curl -s -X POST $(BASE_URL)/api/dev/reset-all | jq . 2>/dev/null || true
	echo ""; echo "$(YELLOW)3️⃣ Clean Stats:$(NC)"
	$(MAKE) dev-stats
	echo ""; echo "$(GREEN)✅ Ready for fresh testing!$(NC)"

full-cycle: ## Full test cycle (reset → onboard → login → next steps)
	@echo "$(BLUE)🚀 Full Test Cycle:$(NC)"
	echo ""; echo "$(YELLOW)1️⃣ Resetting database...$(NC)"
	curl -s -X POST $(BASE_URL)/api/dev/reset-all | jq -r '.message' 2>/dev/null || true
	echo ""; echo "$(YELLOW)2️⃣ Creating tenant (Akkayalar Tekstil)...$(NC)"
	curl -s -X POST $(BASE_URL)/api/admin/onboarding/tenant \
	  -H "Content-Type: application/json" \
	  -d '{"companyName":"Akkayalar Tekstil Dokuma San. Tic. Ltd.Sti","taxId":"4420543162","companyType":"WEAVER","adminFirstName":"Fatih","adminLastName":"Akkaya","adminContact":"fatih@akkayalartekstil.com.tr","selectedOS":["LoomOS","AccountOS"],"trialDays":90}' \
	  | jq -r '"Company: " + .data.companyName + "\nSetup URL: " + .data.setupUrl + "\nToken: " + .data.registrationToken' 2>/dev/null || true
	echo ""; echo "$(GREEN)✅ Tenant created! Use token above for password setup.$(NC)"
	echo "$(YELLOW)Next: Run password setup in Postman with the token$(NC)"

dev-workflow: ## Dev workflow reminders
	@echo "$(BLUE)📚 Development Workflow:$(NC)"
	echo ""; echo "$(YELLOW)1) Infra$(NC) : make dev  (or: make up)"
	echo "$(YELLOW)2) App$(NC)   : make app-run"
	echo "$(YELLOW)3) Health$(NC) : make health"
	echo "$(YELLOW)4) Stats$(NC)  : make dev-stats"
	echo "$(YELLOW)5) Postman$(NC): import postman collection"
	echo "$(YELLOW)6) Reset$(NC)  : make dev-reset"
	echo ""; echo "$(GREEN)💡 Tip: 'make quick-test' for rapid reset$(NC)"

# =============================================================================
# GIT HELPERS / INFO
# =============================================================================
git-status: ## Show detailed git status
	git status
	echo "\n$(YELLOW)Recent commits:$(NC)"
	git log --oneline -5

git-branch: ## Show current branch
	git branch --show-current

git-diff: ## Show unstaged changes
	git diff

info: ## Show system info
	@echo "$(BLUE)📋 System Information:$(NC)"
	@echo "$(YELLOW)Java Version:$(NC)";  java -version 2>&1 | head -1
	@echo "$(YELLOW)Maven Version:$(NC)"; mvn -version | head -1
	@echo "$(YELLOW)Docker Version:$(NC)"; docker --version
	@echo "$(YELLOW)Docker Compose:$(NC)"; docker compose version

endpoints: ## Show available API endpoints (quick list)
	@echo "$(BLUE)📡 API Endpoints:$(NC)"
	echo ""; echo "$(YELLOW)Health & Monitoring:$(NC)"
	echo "  $(BASE_URL)/api/health"
	echo "  $(BASE_URL)/api/info"
	echo "  $(BASE_URL)/actuator/health"
	echo "  $(BASE_URL)/swagger-ui.html"
	echo ""; echo "$(YELLOW)Onboarding:$(NC)"
	echo "  POST $(BASE_URL)/api/admin/onboarding/tenant"
	echo "  POST $(BASE_URL)/api/public/signup"
	echo "  POST $(BASE_URL)/api/auth/setup-password"
	echo ""; echo "$(YELLOW)Auth:$(NC)"
	echo "  POST $(BASE_URL)/api/auth/login"
	echo "  POST $(BASE_URL)/api/auth/register/check"
	echo "  POST $(BASE_URL)/api/auth/register/verify"
	echo ""; echo "$(YELLOW)Companies:$(NC)"
	echo "  GET/POST $(BASE_URL)/api/common/companies"
	echo ""; echo "$(YELLOW)Users:$(NC)"
	echo "  GET/POST $(BASE_URL)/api/common/users"
	echo ""; echo "$(YELLOW)Dev Tools (local):$(NC)"
	echo "  POST $(BASE_URL)/api/dev/reset-all"
	echo "  POST $(BASE_URL)/api/dev/clean-tokens"
	echo "  GET  $(BASE_URL)/api/dev/stats"
	echo ""; echo "$(YELLOW)Monitoring:$(NC)"
	echo "  Prometheus: http://localhost:$(PROM_PORT)"
	echo "  Grafana   : http://localhost:$(GRAFANA_PORT) (admin/admin)"