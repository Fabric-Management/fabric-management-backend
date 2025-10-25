# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE (Modular Monolith)
# =============================================================================
# Production-ready development & deployment commands

.PHONY: help setup validate-env build test clean deploy down restart logs status health db-shell db-migrate app-run app-build

.DEFAULT_GOAL := help

# Colors
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m

# =============================================================================
# HELP
# =============================================================================
help: ## Show available commands
	@echo "$(GREEN)============================================$(NC)"
	@echo "$(GREEN)  Fabric Management - Modular Monolith$(NC)"
	@echo "$(GREEN)============================================$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(BLUE)%-25s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(YELLOW)Quick Start:$(NC)"
	@echo "  make dev          # Start PostgreSQL + Redis"
	@echo "  make app-run      # Run Spring Boot app"
	@echo "  make health       # Check application health"
	@echo ""

# =============================================================================
# SETUP
# =============================================================================
setup: ## Initial setup - create .env from template
	@echo "$(YELLOW)🔧 Setting up environment...$(NC)"
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo "$(GREEN)✅ .env created. Update with your values.$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  .env already exists.$(NC)"; \
	fi

validate-env: ## Validate .env file exists
	@echo "$(YELLOW)🔍 Validating environment...$(NC)"
	@if [ ! -f .env ]; then \
		echo "$(RED)❌ .env not found! Run: make setup$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✅ Environment valid$(NC)"

# =============================================================================
# APPLICATION BUILD & RUN
# =============================================================================
app-build: ## Build Spring Boot application (Maven)
	@echo "$(YELLOW)🏗️  Building application...$(NC)"
	@mvn clean package -DskipTests
	@echo "$(GREEN)✅ Application built → target/fabric-management-backend-1.0.0-SNAPSHOT.jar$(NC)"

app-run: ## Run Spring Boot application
	@echo "$(YELLOW)🚀 Running application...$(NC)"
	@mvn spring-boot:run -Dspring-boot.run.profiles=local

app-test: ## Run all tests
	@echo "$(YELLOW)🧪 Running tests...$(NC)"
	@mvn test
	@echo "$(GREEN)✅ Tests completed$(NC)"

app-integration-test: ## Run integration tests
	@echo "$(YELLOW)🧪 Running integration tests...$(NC)"
	@mvn verify
	@echo "$(GREEN)✅ Integration tests completed$(NC)"

# =============================================================================
# DOCKER INFRASTRUCTURE
# =============================================================================
dev: validate-env ## Start PostgreSQL + Redis (fast dev mode)
	@echo "$(YELLOW)🚀 Starting development infrastructure...$(NC)"
	@docker compose up -d postgres redis
	@echo "$(GREEN)✅ Infrastructure started$(NC)"
	@sleep 3
	@make status

deploy-infra: validate-env ## Deploy full infrastructure (PostgreSQL, Redis, Kafka, Monitoring)
	@echo "$(YELLOW)🚀 Deploying infrastructure...$(NC)"
	@docker compose up -d postgres redis kafka prometheus grafana
	@echo "$(GREEN)✅ Infrastructure deployed$(NC)"
	@sleep 5
	@make status

deploy-all: validate-env ## Deploy infrastructure + monitoring
	@echo "$(YELLOW)🚀 Deploying complete system...$(NC)"
	@docker compose up -d
	@echo "$(GREEN)✅ System deployed$(NC)"
	@sleep 10
	@make status

# =============================================================================
# MANAGEMENT
# =============================================================================
down: ## Stop all Docker services
	@echo "$(YELLOW)🛑 Stopping services...$(NC)"
	@docker compose down
	@echo "$(GREEN)✅ Services stopped$(NC)"

down-clean: ## Stop services + remove volumes (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Stopping services & removing volumes...$(NC)"
	@docker compose down -v
	@echo "$(GREEN)✅ Services stopped, volumes removed$(NC)"

restart: ## Restart all Docker services
	@echo "$(YELLOW)🔄 Restarting services...$(NC)"
	@docker compose restart
	@echo "$(GREEN)✅ Services restarted$(NC)"

restart-db: ## Restart PostgreSQL only
	@echo "$(YELLOW)🔄 Restarting PostgreSQL...$(NC)"
	@docker compose restart postgres
	@echo "$(GREEN)✅ PostgreSQL restarted$(NC)"

# =============================================================================
# MONITORING
# =============================================================================
status: ## Show Docker service status
	@echo "$(YELLOW)📊 Service Status:$(NC)"
	@docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

logs: ## Show logs from all services
	@docker compose logs -f --tail=100

logs-db: ## Show PostgreSQL logs
	@docker compose logs -f --tail=100 postgres

logs-redis: ## Show Redis logs
	@docker compose logs -f --tail=100 redis

logs-errors: ## Show ERROR/EXCEPTION logs
	@docker compose logs --tail=200 | grep -iE "error|exception|failed" || echo "$(GREEN)✅ No errors$(NC)"

health: ## Check application health
	@echo "$(BLUE)🏥 Health Check:$(NC)"
	@echo "\n$(YELLOW)Application Health:$(NC)"
	@curl -s http://localhost:8080/api/health | jq . 2>/dev/null || echo "$(RED)❌ Application not responding$(NC)"
	@echo "\n$(YELLOW)Actuator Health:$(NC)"
	@curl -s http://localhost:8080/actuator/health | jq . 2>/dev/null || echo "$(RED)❌ Actuator not responding$(NC)"
	@echo "\n$(YELLOW)PostgreSQL:$(NC)"
	@docker compose ps postgres | grep -q "(healthy)" && echo "$(GREEN)✅ Healthy$(NC)" || echo "$(RED)❌ Unhealthy$(NC)"
	@echo "\n$(YELLOW)Redis:$(NC)"
	@docker compose ps redis | grep -q "(healthy)" && echo "$(GREEN)✅ Healthy$(NC)" || echo "$(RED)❌ Unhealthy$(NC)"

ps: ## Show running Docker containers
	@docker ps --filter "name=fabric-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# =============================================================================
# DATABASE (PostgreSQL)
# =============================================================================
db-shell: ## Open PostgreSQL shell
	@docker exec -it fabric-postgres psql -U fabric_user -d fabric_management

db-migrate: ## Run Flyway migrations manually
	@echo "$(YELLOW)🗄️  Running database migrations...$(NC)"
	@mvn flyway:migrate
	@echo "$(GREEN)✅ Migrations completed$(NC)"

db-info: ## Show Flyway migration status
	@echo "$(YELLOW)📊 Migration Status:$(NC)"
	@mvn flyway:info

db-validate: ## Validate migrations
	@echo "$(YELLOW)🔍 Validating migrations...$(NC)"
	@mvn flyway:validate
	@echo "$(GREEN)✅ Migrations valid$(NC)"

db-clean: ## Clean database (DESTRUCTIVE! Drops all objects)
	@echo "$(RED)⚠️  WARNING: This will drop all database objects!$(NC)"
	@read -p "Are you sure? (yes/no): " confirm; \
	if [ "$$confirm" = "yes" ]; then \
		mvn flyway:clean; \
		echo "$(GREEN)✅ Database cleaned$(NC)"; \
	else \
		echo "$(YELLOW)Cancelled$(NC)"; \
	fi

db-tables: ## List all tables
	@echo "$(YELLOW)📊 Database Tables:$(NC)"
	@docker exec -it fabric-postgres psql -U fabric_user -d fabric_management -c "\
		SELECT \
			schemaname AS schema, \
			tablename AS table, \
			pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size \
		FROM pg_tables \
		WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'flyway') \
		ORDER BY schemaname, tablename;"

db-schemas: ## List all schemas
	@echo "$(YELLOW)📂 Database Schemas:$(NC)"
	@docker exec -it fabric-postgres psql -U fabric_user -d fabric_management -c "\
		SELECT schema_name \
		FROM information_schema.schemata \
		WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'flyway') \
		ORDER BY schema_name;"

db-backup: ## Backup database
	@echo "$(YELLOW)💾 Backing up database...$(NC)"
	@mkdir -p backups
	@docker exec -t fabric-postgres pg_dump -U fabric_user fabric_management > backups/backup-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)✅ Database backed up to backups/$(NC)"

db-restore: ## Restore database (make db-restore FILE=backups/backup.sql)
	@echo "$(YELLOW)📥 Restoring database...$(NC)"
	@if [ -z "$(FILE)" ]; then \
		echo "$(RED)❌ Specify: make db-restore FILE=backup.sql$(NC)"; \
		exit 1; \
	fi
	@docker exec -i fabric-postgres psql -U fabric_user fabric_management < $(FILE)
	@echo "$(GREEN)✅ Database restored$(NC)"

db-reset: ## Reset database (drop + recreate + migrate)
	@echo "$(RED)⚠️  Resetting database...$(NC)"
	@docker compose down postgres
	@docker volume rm fabric-management-backend_postgres_data 2>/dev/null || true
	@docker compose up -d postgres
	@echo "$(YELLOW)⏳ Waiting for PostgreSQL to be ready...$(NC)"
	@sleep 5
	@echo "$(GREEN)✅ Database reset complete. Flyway will run on next app start.$(NC)"

# =============================================================================
# KAFKA MANAGEMENT
# =============================================================================
kafka-topics: ## List all Kafka topics
	@echo "$(YELLOW)📋 Kafka Topics:$(NC)"
	@docker exec fabric-kafka kafka-topics --bootstrap-server localhost:9092 --list

kafka-describe: ## Describe Kafka topic (make kafka-describe TOPIC=user.created)
	@if [ -z "$(TOPIC)" ]; then \
		echo "$(RED)❌ Specify: make kafka-describe TOPIC=topic-name$(NC)"; \
		exit 1; \
	fi
	@docker exec fabric-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic $(TOPIC)

kafka-consumer: ## Consume messages (make kafka-consumer TOPIC=user.created)
	@if [ -z "$(TOPIC)" ]; then \
		echo "$(RED)❌ Specify: make kafka-consumer TOPIC=topic-name$(NC)"; \
		exit 1; \
	fi
	@echo "$(YELLOW)📨 Consuming from: $(TOPIC)$(NC)"
	@docker exec -it fabric-kafka kafka-console-consumer \
		--bootstrap-server localhost:9092 \
		--topic $(TOPIC) \
		--from-beginning

# =============================================================================
# CLEANUP
# =============================================================================
clean: ## Clean Maven build artifacts
	@echo "$(YELLOW)🧹 Cleaning build artifacts...$(NC)"
	@mvn clean
	@echo "$(GREEN)✅ Clean completed$(NC)"

clean-docker: ## Remove Docker images (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Removing Docker images...$(NC)"
	@docker compose down --rmi all
	@echo "$(GREEN)✅ Docker images removed$(NC)"

prune: ## Clean Docker system
	@echo "$(YELLOW)🧹 Pruning Docker system...$(NC)"
	@docker system prune -f
	@echo "$(GREEN)✅ Docker system pruned$(NC)"

rebuild: ## Full rebuild (clean Docker + Maven + deploy)
	@echo "$(YELLOW)🧹 Full clean rebuild...$(NC)"
	@make down-clean
	@make clean
	@make app-build
	@make deploy-all
	@echo "$(GREEN)✅ Rebuild completed$(NC)"

# =============================================================================
# CODE QUALITY
# =============================================================================
lint: ## Check code quality
	@echo "$(YELLOW)🔍 Checking code quality...$(NC)"
	@mvn verify -DskipTests
	@echo "$(GREEN)✅ Code quality check completed$(NC)"

format: ## Format code
	@echo "$(YELLOW)💅 Formatting code...$(NC)"
	@mvn spotless:apply 2>/dev/null || echo "$(YELLOW)⚠️  Spotless not configured$(NC)"
	@echo "$(GREEN)✅ Code formatted$(NC)"

# =============================================================================
# MONITORING
# =============================================================================
metrics: ## Show Prometheus metrics
	@echo "$(YELLOW)📊 Application Metrics:$(NC)"
	@curl -s http://localhost:8080/actuator/metrics | jq . 2>/dev/null || echo "$(RED)❌ Metrics not available$(NC)"

prometheus: ## Open Prometheus UI
	@echo "$(BLUE)📊 Opening Prometheus...$(NC)"
	@open http://localhost:9090 || xdg-open http://localhost:9090 2>/dev/null || echo "Visit: http://localhost:9090"

grafana: ## Open Grafana UI
	@echo "$(BLUE)📊 Opening Grafana...$(NC)"
	@open http://localhost:3000 || xdg-open http://localhost:3000 2>/dev/null || echo "Visit: http://localhost:3000 (admin/admin)"

swagger: ## Open Swagger UI
	@echo "$(BLUE)📖 Opening Swagger UI...$(NC)"
	@open http://localhost:8080/swagger-ui.html || xdg-open http://localhost:8080/swagger-ui.html 2>/dev/null || echo "Visit: http://localhost:8080/swagger-ui.html"

# =============================================================================
# TESTING
# =============================================================================
test: ## Run unit tests
	@echo "$(YELLOW)🧪 Running unit tests...$(NC)"
	@mvn test
	@echo "$(GREEN)✅ Unit tests completed$(NC)"

test-integration: ## Run integration tests
	@echo "$(YELLOW)🧪 Running integration tests...$(NC)"
	@mvn verify
	@echo "$(GREEN)✅ Integration tests completed$(NC)"

test-coverage: ## Generate test coverage report
	@echo "$(YELLOW)📊 Generating coverage report...$(NC)"
	@mvn jacoco:report
	@echo "$(GREEN)✅ Coverage report: target/site/jacoco/index.html$(NC)"

# =============================================================================
# DEVELOPMENT SHORTCUTS
# =============================================================================
dev-quick: ## Quick start (PostgreSQL + Redis only)
	@make dev

dev-full: ## Full stack (All infrastructure + Monitoring)
	@make deploy-all

dev-clean-start: ## Clean restart (remove volumes + rebuild)
	@make down-clean
	@make dev
	@echo "$(GREEN)✅ Clean start ready. Run: make app-run$(NC)"

# =============================================================================
# GIT HELPERS
# =============================================================================
git-status: ## Show detailed git status
	@git status
	@echo "\n$(YELLOW)Recent commits:$(NC)"
	@git log --oneline -5

git-branch: ## Show current branch
	@git branch --show-current

git-diff: ## Show unstaged changes
	@git diff

# =============================================================================
# USEFUL INFO
# =============================================================================
info: ## Show system info
	@echo "$(BLUE)📋 System Information:$(NC)"
	@echo "$(YELLOW)Java Version:$(NC)"
	@java -version 2>&1 | head -1
	@echo "$(YELLOW)Maven Version:$(NC)"
	@mvn -version | head -1
	@echo "$(YELLOW)Docker Version:$(NC)"
	@docker --version
	@echo "$(YELLOW)Docker Compose Version:$(NC)"
	@docker compose version

endpoints: ## Show available API endpoints
	@echo "$(BLUE)📡 API Endpoints:$(NC)"
	@echo "$(YELLOW)Health:$(NC)        http://localhost:8080/api/health"
	@echo "$(YELLOW)Info:$(NC)          http://localhost:8080/api/info"
	@echo "$(YELLOW)Swagger:$(NC)       http://localhost:8080/swagger-ui.html"
	@echo "$(YELLOW)Actuator:$(NC)      http://localhost:8080/actuator"
	@echo "$(YELLOW)Metrics:$(NC)       http://localhost:8080/actuator/metrics"
	@echo "$(YELLOW)Prometheus:$(NC)    http://localhost:9090"
	@echo "$(YELLOW)Grafana:$(NC)       http://localhost:3000 (admin/admin)"

# =============================================================================
# ALIASES (Backward Compatibility)
# =============================================================================
build: app-build ## Alias for app-build

run: app-run ## Alias for app-run

deploy: deploy-infra ## Alias for deploy-infra
