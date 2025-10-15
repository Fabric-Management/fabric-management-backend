# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE
# =============================================================================
# Professional deployment and development commands

.PHONY: help build test clean deploy down restart logs status health

# Default target
.DEFAULT_GOAL := help

# Colors
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m # No Color

# =============================================================================
# HELP
# =============================================================================
help: ## Show this help message
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)  Fabric Management - Make Commands$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(BLUE)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

# =============================================================================
# DEVELOPMENT
# =============================================================================
setup: ## Initial setup - Copy .env.example to .env
	@echo "$(YELLOW)🔧 Setting up environment...$(NC)"
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo "$(GREEN)✅ .env file created. Please update it with your values.$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  .env file already exists.$(NC)"; \
	fi

validate-env: ## Validate environment variables
	@echo "$(YELLOW)🔍 Validating environment variables...$(NC)"
	@if [ ! -f .env ]; then \
		echo "$(RED)❌ .env file not found!$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✅ Environment file exists$(NC)"

# =============================================================================
# BUILD
# =============================================================================
build: ## Build all services
	@echo "$(YELLOW)🏗️  Building all services...$(NC)"
	mvn clean install -DskipTests
	@echo "$(GREEN)✅ Build completed$(NC)"

build-services: ## Build Docker images for all services
	@echo "$(YELLOW)🐳 Building Docker images...$(NC)"
	docker-compose -f docker-compose-complete.yml build
	@echo "$(GREEN)✅ Docker images built$(NC)"

build-service: ## Build specific service (use: make build-service SERVICE=user-service)
	@echo "$(YELLOW)🐳 Building $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Please specify SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker-compose -f docker-compose-complete.yml build $(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) built$(NC)"

# =============================================================================
# TEST
# =============================================================================
test: ## Run all tests
	@echo "$(YELLOW)🧪 Running tests...$(NC)"
	mvn test
	@echo "$(GREEN)✅ Tests completed$(NC)"

test-service: ## Test specific service (use: make test-service SERVICE=user-service)
	@echo "$(YELLOW)🧪 Testing $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Please specify SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	mvn test -pl services/$(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) tests completed$(NC)"

# =============================================================================
# DEPLOYMENT
# =============================================================================
deploy-infra: validate-env ## Deploy infrastructure services (PostgreSQL, Redis, Kafka)
	@echo "$(YELLOW)🚀 Deploying infrastructure...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)✅ Infrastructure deployed$(NC)"
	@echo "$(BLUE)ℹ️  Waiting for services to be healthy...$(NC)"
	@sleep 10
	@make status

deploy: validate-env build-services ## Deploy all services (infrastructure + microservices)
	@echo "$(YELLOW)🚀 Deploying complete system...$(NC)"
	docker-compose -f docker-compose-complete.yml up -d
	@echo "$(GREEN)✅ System deployed$(NC)"
	@echo "$(BLUE)ℹ️  Waiting for services to be healthy...$(NC)"
	@sleep 15
	@make health

deploy-service: ## Deploy specific service (use: make deploy-service SERVICE=user-service)
	@echo "$(YELLOW)🚀 Deploying $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Please specify SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker-compose -f docker-compose-complete.yml up -d $(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) deployed$(NC)"

# =============================================================================
# MANAGEMENT
# =============================================================================
down: ## Stop all services
	@echo "$(YELLOW)🛑 Stopping all services...$(NC)"
	docker-compose -f docker-compose-complete.yml down
	@echo "$(GREEN)✅ Services stopped$(NC)"

down-clean: ## Stop all services and remove volumes
	@echo "$(RED)⚠️  Stopping services and removing volumes...$(NC)"
	docker-compose -f docker-compose-complete.yml down -v
	@echo "$(GREEN)✅ Services stopped and volumes removed$(NC)"

restart: ## Restart all services
	@echo "$(YELLOW)🔄 Restarting services...$(NC)"
	docker-compose -f docker-compose-complete.yml restart
	@echo "$(GREEN)✅ Services restarted$(NC)"

restart-service: ## Restart specific service (use: make restart-service SERVICE=user-service)
	@echo "$(YELLOW)🔄 Restarting $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Please specify SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker-compose -f docker-compose-complete.yml restart $(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) restarted$(NC)"

# =============================================================================
# MONITORING
# =============================================================================
logs: ## Show logs from all services
	docker-compose -f docker-compose-complete.yml logs -f

logs-service: ## Show logs from specific service (use: make logs-service SERVICE=user-service)
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Please specify SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	docker-compose -f docker-compose-complete.yml logs -f $(SERVICE)

status: ## Show status of all containers
	@echo "$(BLUE)📊 Container Status:$(NC)"
	@docker-compose -f docker-compose-complete.yml ps

health: ## Check health of all services
	@echo "$(BLUE)🏥 Health Check:$(NC)"
	@echo ""
	@echo "$(YELLOW)User Service:$(NC)"
	@curl -s http://localhost:8081/actuator/health | jq . || echo "$(RED)❌ User Service not responding$(NC)"
	@echo ""
	@echo "$(YELLOW)Contact Service:$(NC)"
	@curl -s http://localhost:8082/actuator/health | jq . || echo "$(RED)❌ Contact Service not responding$(NC)"
	@echo ""
	@echo "$(YELLOW)Company Service:$(NC)"
	@curl -s http://localhost:8083/actuator/health | jq . || echo "$(RED)❌ Company Service not responding$(NC)"

ps: ## Show running containers
	docker ps --filter "name=fabric-*" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# =============================================================================
# DATABASE
# =============================================================================
db-migrate: ## Run database migrations
	@echo "$(YELLOW)🗄️  Running migrations...$(NC)"
	@echo "$(BLUE)ℹ️  Migrations run automatically on service startup$(NC)"

db-backup: ## Backup database
	@echo "$(YELLOW)💾 Backing up database...$(NC)"
	docker exec -t fabric-postgres pg_dump -U fabric_user fabric_management > backup-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)✅ Database backed up$(NC)"

db-restore: ## Restore database (use: make db-restore FILE=backup.sql)
	@echo "$(YELLOW)📥 Restoring database...$(NC)"
	@if [ -z "$(FILE)" ]; then \
		echo "$(RED)❌ Please specify FILE=backup.sql$(NC)"; \
		exit 1; \
	fi
	docker exec -i fabric-postgres psql -U fabric_user fabric_management < $(FILE)
	@echo "$(GREEN)✅ Database restored$(NC)"

db-shell: ## Open PostgreSQL shell
	docker exec -it fabric-postgres psql -U fabric_user -d fabric_management

# =============================================================================
# CLEANUP
# =============================================================================
clean: ## Clean build artifacts
	@echo "$(YELLOW)🧹 Cleaning build artifacts...$(NC)"
	mvn clean
	@echo "$(GREEN)✅ Clean completed$(NC)"

clean-docker: ## Remove all Docker images
	@echo "$(RED)⚠️  Removing Docker images...$(NC)"
	docker-compose -f docker-compose-complete.yml down --rmi all
	@echo "$(GREEN)✅ Docker images removed$(NC)"

prune: ## Clean Docker system (dangling images, networks, etc.)
	@echo "$(YELLOW)🧹 Pruning Docker system...$(NC)"
	docker system prune -f
	@echo "$(GREEN)✅ Docker system pruned$(NC)"

# =============================================================================
# DEVELOPMENT TOOLS
# =============================================================================
format: ## Format code
	@echo "$(YELLOW)💅 Formatting code...$(NC)"
	mvn spotless:apply
	@echo "$(GREEN)✅ Code formatted$(NC)"

lint: ## Lint code
	@echo "$(YELLOW)🔍 Linting code...$(NC)"
	mvn spotless:check
	@echo "$(GREEN)✅ Linting completed$(NC)"

# =============================================================================
# FAST DEVELOPMENT COMMANDS (Hot Reload)
# =============================================================================
dev-restart-gateway: ## Fast restart: API Gateway only (~30s)
	@echo "$(YELLOW)⚡ Fast restart: API Gateway...$(NC)"
	docker compose restart api-gateway
	@echo "$(GREEN)✅ Gateway restarted (config changes applied)$(NC)"

dev-restart-user: ## Fast restart: User Service only (~30s)
	@echo "$(YELLOW)⚡ Fast restart: User Service...$(NC)"
	docker compose restart user-service
	@echo "$(GREEN)✅ User Service restarted$(NC)"

dev-restart-contact: ## Fast restart: Contact Service only (~30s)
	@echo "$(YELLOW)⚡ Fast restart: Contact Service...$(NC)"
	docker compose restart contact-service
	@echo "$(GREEN)✅ Contact Service restarted$(NC)"

dev-restart-company: ## Fast restart: Company Service only (~30s)
	@echo "$(YELLOW)⚡ Fast restart: Company Service...$(NC)"
	docker compose restart company-service
	@echo "$(GREEN)✅ Company Service restarted$(NC)"

dev-rebuild-user: ## Fast rebuild + restart: User Service (~2min)
	@echo "$(YELLOW)⚡ Rebuilding User Service...$(NC)"
	mvn clean package -pl services/user-service -am -DskipTests
	docker compose up -d --build --no-deps user-service
	@echo "$(GREEN)✅ User Service rebuilt and restarted$(NC)"

dev-rebuild-user-docker: ## Docker-only rebuild: User Service (no mvn needed, ~3min)
	@echo "$(YELLOW)⚡ Docker rebuilding User Service (includes Maven build)...$(NC)"
	docker compose up -d --build --no-deps user-service
	@echo "$(GREEN)✅ User Service rebuilt and restarted$(NC)"

dev-rebuild-contact: ## Fast rebuild + restart: Contact Service (~2min)
	@echo "$(YELLOW)⚡ Rebuilding Contact Service...$(NC)"
	mvn clean package -pl services/contact-service -am -DskipTests
	docker compose up -d --build --no-deps contact-service
	@echo "$(GREEN)✅ Contact Service rebuilt and restarted$(NC)"

dev-rebuild-company: ## Fast rebuild + restart: Company Service (~2min)
	@echo "$(YELLOW)⚡ Rebuilding Company Service...$(NC)"
	mvn clean package -pl services/company-service -am -DskipTests
	docker compose up -d --build --no-deps company-service
	@echo "$(GREEN)✅ Company Service rebuilt and restarted$(NC)"

dev-rebuild-gateway: ## Fast rebuild + restart: API Gateway (~1min)
	@echo "$(YELLOW)⚡ Rebuilding API Gateway...$(NC)"
	mvn clean package -pl services/api-gateway -am -DskipTests
	docker compose up -d --build --no-deps api-gateway
	@echo "$(GREEN)✅ Gateway rebuilt and restarted$(NC)"

dev-rebuild-gateway-docker: ## Docker-only rebuild: API Gateway (no mvn needed, ~2min)
	@echo "$(YELLOW)⚡ Docker rebuilding API Gateway (includes Maven build)...$(NC)"
	docker compose up -d --build --no-deps api-gateway
	@echo "$(GREEN)✅ Gateway rebuilt and restarted$(NC)"

dev-rebuild-all-services: ## Rebuild all services (User, Contact, Company) - ~5min
	@echo "$(YELLOW)⚡ Rebuilding all microservices...$(NC)"
	docker compose up -d --build --no-deps user-service contact-service company-service
	@echo "$(GREEN)✅ All services rebuilt and restarted$(NC)"
	@echo "$(BLUE)ℹ️  Wait 30s for health checks, then test!$(NC)"

dev-logs-gateway: ## Tail logs: API Gateway
	docker compose logs -f --tail=100 api-gateway

dev-logs-gateway-last: ## Show last 30 lines: API Gateway (no follow)
	@docker compose logs --tail=30 api-gateway

dev-logs-user: ## Tail logs: User Service
	docker compose logs -f --tail=100 user-service

dev-logs-user-last: ## Show last 30 lines: User Service (no follow)
	@docker compose logs --tail=30 user-service

dev-logs-all: ## Tail logs: All services
	docker compose logs -f --tail=50

dev-logs-errors: ## Show ERROR logs from all services
	@docker compose logs --tail=100 | grep -i "error\|exception\|failed" || echo "$(GREEN)✅ No errors found$(NC)"

dev-logs-user-full: ## Show last 100 lines: User Service (full startup logs)
	@docker compose logs --tail=100 user-service

dev-logs-user-startup: ## Show User Service startup logs (look for "Started" message)
	@docker compose logs user-service | grep -E "Started|Tomcat|JVM|Application" | head -30

dev-logs-user-500: ## Show User Service logs related to 500 errors
	@docker compose logs --tail=300 user-service | grep -v "Kafka" | tail -50

dev-test-network: ## Test network connectivity between services
	@echo "$(YELLOW)🔍 Testing network connectivity...$(NC)"
	@docker exec fabric-api-gateway nc -zv user-service 8081 2>&1 || echo "$(RED)❌ Cannot reach user-service$(NC)"
	@docker exec fabric-api-gateway curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://user-service:8081/actuator/health || echo "$(RED)❌ User Service health check failed$(NC)"

dev-logs-gateway-error: ## Show Gateway ERROR/WARN logs
	@docker compose logs --tail=200 api-gateway | grep -E "ERROR|WARN|Exception|Failed" | head -50

# =============================================================================
# QUICK COMMANDS
# =============================================================================
rebuild-all: ## Full rebuild: mvn clean install + docker build + docker up (~10min)
	@echo "$(YELLOW)🔨 Full rebuild: Maven + Docker...$(NC)"
	mvn clean install -DskipTests
	docker compose build --no-cache && docker compose up -d
	@echo "$(GREEN)✅ Full rebuild completed$(NC)"

up: deploy ## Alias for deploy
stop: down ## Alias for down
start: deploy ## Alias for deploy

