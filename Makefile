# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE
# =============================================================================
# Production-ready development & deployment commands

.PHONY: help setup validate-env build test clean deploy down restart logs status health db-shell rebuild-service restart-service logs-service

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
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)  Fabric Management - Make Commands$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(BLUE)%-25s$(NC) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(YELLOW)Examples:$(NC)"
	@echo "  make rebuild-service SERVICE=user-service"
	@echo "  make logs-service SERVICE=contact-service"
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
# BUILD
# =============================================================================
build: ## Build all services (Maven)
	@echo "$(YELLOW)🏗️  Building all services...$(NC)"
	mvn clean install -DskipTests
	@echo "$(GREEN)✅ Build completed$(NC)"

build-service: ## Build specific service (make build-service SERVICE=user-service)
	@echo "$(YELLOW)🏗️  Building $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make build-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	mvn clean package -pl services/$(SERVICE) -am -DskipTests
	@echo "$(GREEN)✅ $(SERVICE) built$(NC)"

rebuild-service: ## Rebuild + restart service (make rebuild-service SERVICE=user-service)
	@echo "$(YELLOW)⚡ Rebuilding $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make rebuild-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	mvn clean package -pl services/$(SERVICE) -am -DskipTests
	docker compose up -d --build --no-deps fabric-$(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) rebuilt & restarted$(NC)"

rebuild-all: ## Rebuild all services (Maven + Docker)
	@echo "$(YELLOW)⚡ Rebuilding all services...$(NC)"
	mvn clean install -DskipTests
	docker compose up -d --build user-service contact-service company-service api-gateway
	@echo "$(GREEN)✅ All services rebuilt$(NC)"

# =============================================================================
# TEST
# =============================================================================
test: ## Run all tests
	@echo "$(YELLOW)🧪 Running tests...$(NC)"
	mvn test
	@echo "$(GREEN)✅ Tests completed$(NC)"

test-service: ## Test specific service (make test-service SERVICE=user-service)
	@echo "$(YELLOW)🧪 Testing $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make test-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	mvn test -pl services/$(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) tests completed$(NC)"

# =============================================================================
# DEPLOYMENT
# =============================================================================
deploy-infra: validate-env ## Deploy infrastructure (PostgreSQL, Redis, Kafka)
	@echo "$(YELLOW)🚀 Deploying infrastructure...$(NC)"
	docker compose up -d postgres redis kafka
	@echo "$(GREEN)✅ Infrastructure deployed$(NC)"
	@sleep 5
	@make status

deploy: validate-env ## Deploy all services
	@echo "$(YELLOW)🚀 Deploying complete system...$(NC)"
	docker compose up -d
	@echo "$(GREEN)✅ System deployed$(NC)"
	@sleep 10
	@make health

# =============================================================================
# MANAGEMENT
# =============================================================================
down: ## Stop all services
	@echo "$(YELLOW)🛑 Stopping services...$(NC)"
	docker compose down
	@echo "$(GREEN)✅ Services stopped$(NC)"

down-clean: ## Stop services + remove volumes (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Stopping services & removing volumes...$(NC)"
	docker compose down -v
	@echo "$(GREEN)✅ Services stopped, volumes removed$(NC)"

restart: ## Restart all services
	@echo "$(YELLOW)🔄 Restarting services...$(NC)"
	docker compose restart
	@echo "$(GREEN)✅ Services restarted$(NC)"

restart-service: ## Restart specific service (make restart-service SERVICE=user-service)
	@echo "$(YELLOW)🔄 Restarting $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make restart-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker compose restart fabric-$(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) restarted$(NC)"

# =============================================================================
# MONITORING
# =============================================================================
status: ## Show service status
	@echo "$(YELLOW)📊 Service Status:$(NC)"
	@docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

logs: ## Show logs from all services
	docker compose logs -f --tail=100

logs-service: ## Show logs from service (make logs-service SERVICE=user-service)
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make logs-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker compose logs -f --tail=100 fabric-$(SERVICE)

logs-errors: ## Show ERROR/EXCEPTION logs
	@docker compose logs --tail=200 | grep -iE "error|exception|failed" || echo "$(GREEN)✅ No errors$(NC)"

health: ## Check service health
	@echo "$(BLUE)🏥 Health Check:$(NC)"
	@echo "\n$(YELLOW)User Service:$(NC)"
	@curl -s http://localhost:8081/actuator/health | jq . 2>/dev/null || echo "$(RED)❌ Not responding$(NC)"
	@echo "\n$(YELLOW)Contact Service:$(NC)"
	@curl -s http://localhost:8082/actuator/health | jq . 2>/dev/null || echo "$(RED)❌ Not responding$(NC)"
	@echo "\n$(YELLOW)Company Service:$(NC)"
	@curl -s http://localhost:8083/actuator/health | jq . 2>/dev/null || echo "$(RED)❌ Not responding$(NC)"

ps: ## Show running containers
	@docker ps --filter "name=fabric-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# =============================================================================
# DATABASE
# =============================================================================
db-shell: ## Open PostgreSQL shell
	docker exec -it fabric-postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}

db-backup: ## Backup database
	@echo "$(YELLOW)💾 Backing up database...$(NC)"
	docker exec -t fabric-postgres pg_dump -U ${POSTGRES_USER} ${POSTGRES_DB} > backup-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)✅ Database backed up$(NC)"

db-restore: ## Restore database (make db-restore FILE=backup.sql)
	@echo "$(YELLOW)📥 Restoring database...$(NC)"
	@if [ -z "$(FILE)" ]; then \
		echo "$(RED)❌ Specify: make db-restore FILE=backup.sql$(NC)"; \
		exit 1; \
	fi
	docker exec -i fabric-postgres psql -U ${POSTGRES_USER} ${POSTGRES_DB} < $(FILE)
	@echo "$(GREEN)✅ Database restored$(NC)"

# =============================================================================
# CLEANUP
# =============================================================================
clean: ## Clean Maven build artifacts
	@echo "$(YELLOW)🧹 Cleaning build artifacts...$(NC)"
	mvn clean
	@echo "$(GREEN)✅ Clean completed$(NC)"

clean-docker: ## Remove Docker images (DESTRUCTIVE!)
	@echo "$(RED)⚠️  Removing Docker images...$(NC)"
	docker compose down --rmi all
	@echo "$(GREEN)✅ Docker images removed$(NC)"

prune: ## Clean Docker system (dangling resources)
	@echo "$(YELLOW)🧹 Pruning Docker system...$(NC)"
	docker system prune -f
	@echo "$(GREEN)✅ Docker system pruned$(NC)"

# =============================================================================
# CODE QUALITY
# =============================================================================
format: ## Format code (Spotless)
	@echo "$(YELLOW)💅 Formatting code...$(NC)"
	mvn spotless:apply
	@echo "$(GREEN)✅ Code formatted$(NC)"

lint: ## Lint code (Spotless check)
	@echo "$(YELLOW)🔍 Linting code...$(NC)"
	mvn spotless:check
	@echo "$(GREEN)✅ Linting completed$(NC)"

# =============================================================================
# DEVELOPMENT SHORTCUTS
# =============================================================================
dev: deploy-infra ## Start infrastructure only (fast dev mode)

dev-all: deploy ## Start everything

dev-test: ## Quick test cycle (build + restart service)
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make dev-test SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	@make rebuild-service SERVICE=$(SERVICE)
	@sleep 5
	@make logs-service SERVICE=$(SERVICE)
