# =============================================================================
# FABRIC MANAGEMENT SYSTEM - MAKEFILE
# =============================================================================
# Production-ready development & deployment commands

.PHONY: help setup validate-env build test clean deploy down restart logs status health db-shell rebuild-service restart-service logs-service kafka-topics kafka-describe kafka-delete kafka-consumer

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
build: ## Build all services (Maven + Docker)
	@echo "$(YELLOW)🏗️  Building all services...$(NC)"
	@mvn clean install -DskipTests
	@docker compose up -d
	@docker compose build --no-cache
	@echo "$(GREEN)✅ Build completed & services started$(NC)"
	@sleep 200
	@make status

build-shared: ## Build all shared modules (domain, infrastructure, security)
	@echo "$(YELLOW)🏗️  Building shared modules...$(NC)"
	@cd shared/shared-domain && mvn clean install -DskipTests
	@cd shared/shared-application && mvn clean install -DskipTests
	@cd shared/shared-infrastructure && mvn clean install -DskipTests
	@cd shared/shared-security && mvn clean install -DskipTests
	@echo "$(GREEN)✅ Shared modules built$(NC)"

build-service: ## Build specific service (make build-service SERVICE=user-service)
	@echo "$(YELLOW)🏗️  Building $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make build-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	@mvn clean package -pl services/$(SERVICE) -am -DskipTests
	@echo "$(GREEN)✅ $(SERVICE) built$(NC)"

rebuild-service: ## Rebuild + restart service (make rebuild-service SERVICE=user-service)
	@echo "$(YELLOW)⚡ Rebuilding $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make rebuild-service SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	@mvn clean package -pl services/$(SERVICE) -am -DskipTests
	@docker compose up -d --build --no-deps fabric-$(SERVICE)
	@echo "$(GREEN)✅ $(SERVICE) rebuilt & restarted$(NC)"

rebuild-with-shared: ## Rebuild shared + service + restart (make rebuild-with-shared SERVICE=user-service)
	@echo "$(YELLOW)⚡ Rebuilding shared + $(SERVICE)...$(NC)"
	@if [ -z "$(SERVICE)" ]; then \
		echo "$(RED)❌ Specify: make rebuild-with-shared SERVICE=service-name$(NC)"; \
		exit 1; \
	fi
	@make build-shared
	@mvn clean package -pl services/$(SERVICE) -DskipTests
	@docker compose up -d --build --no-deps fabric-$(SERVICE)
	@echo "$(GREEN)✅ Shared + $(SERVICE) rebuilt & restarted$(NC)"

rebuild: ## 🔄 Full clean rebuild (remove everything & rebuild all)
	@echo "$(YELLOW)🧹  Cleaning up all Docker resources...$(NC)"
	@docker compose down --rmi all --volumes --remove-orphans || true
	@docker builder prune -a -f || true
	@docker system prune -a --volumes -f || true
	@echo "$(YELLOW)🏗️  Building all services (Maven + Docker)...$(NC)"
	@mvn clean install -DskipTests
	@echo "$(YELLOW)🚧  Building Docker images without cache...$(NC)"
	@docker compose build --no-cache
	@echo "$(YELLOW)🚀  Starting Docker containers...$(NC)"
	@docker compose up -d
	@echo "$(GREEN)✅  Rebuild completed & services started$(NC)"
	@sleep 240
	@make status

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
	@docker exec -it fabric-postgres psql -U fabricuser -d fabricdb

db-tables: ## List all tables with row counts
	@echo "$(YELLOW)📊 Database Tables:$(NC)"
	@docker exec -it fabric-postgres psql -U fabricuser -d fabricdb -c "\
		SELECT \
			schemaname AS schema, \
			tablename AS table, \
			pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size, \
			(SELECT count(*) FROM information_schema.columns WHERE table_name = tablename) AS columns, \
			(xpath('/row/count/text()', query_to_xml('SELECT count(*) FROM '||schemaname||'.'||tablename, true, false, '')))[1]::text::int AS rows \
		FROM pg_tables \
		WHERE schemaname NOT IN ('pg_catalog', 'information_schema') \
		ORDER BY schemaname, tablename;"

db-data: ## Show tables with data (non-empty)
	@echo "$(YELLOW)📊 Tables with Data:$(NC)"
	@docker exec -it fabric-postgres psql -U fabricuser -d fabricdb -c "\
		SELECT \
			schemaname AS schema, \
			tablename AS table, \
			(xpath('/row/count/text()', query_to_xml('SELECT count(*) FROM '||schemaname||'.'||tablename, true, false, '')))[1]::text::int AS rows \
		FROM pg_tables \
		WHERE schemaname NOT IN ('pg_catalog', 'information_schema') \
		AND (xpath('/row/count/text()', query_to_xml('SELECT count(*) FROM '||schemaname||'.'||tablename, true, false, '')))[1]::text::int > 0 \
		ORDER BY rows DESC;"

db-show: ## Show table data (make db-show TABLE=users LIMIT=10)
	@if [ -z "$(TABLE)" ]; then \
		echo "$(RED)❌ Specify: make db-show TABLE=table-name LIMIT=10$(NC)"; \
		exit 1; \
	fi
	@echo "$(YELLOW)📊 Data from $(TABLE):$(NC)"
	@docker exec -it fabric-postgres psql -U fabricuser -d fabricdb -c "SELECT * FROM $(TABLE) LIMIT $${LIMIT:-10};"

db-count: ## Count rows in all tables
	@echo "$(YELLOW)📊 Row Counts:$(NC)"
	@docker exec -it fabric-postgres psql -U fabricuser -d fabricdb -c "\
		SELECT \
			tablename, \
			(xpath('/row/count/text()', query_to_xml('SELECT count(*) FROM '||schemaname||'.'||tablename, true, false, '')))[1]::text::int AS row_count \
		FROM pg_tables \
		WHERE schemaname = 'public' \
		ORDER BY row_count DESC NULLS LAST;"

db-backup: ## Backup database
	@echo "$(YELLOW)💾 Backing up database...$(NC)"
	@docker exec -t fabric-postgres pg_dump -U fabricuser fabricdb > backup-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)✅ Database backed up$(NC)"

db-restore: ## Restore database (make db-restore FILE=backup.sql)
	@echo "$(YELLOW)📥 Restoring database...$(NC)"
	@if [ -z "$(FILE)" ]; then \
		echo "$(RED)❌ Specify: make db-restore FILE=backup.sql$(NC)"; \
		exit 1; \
	fi
	@docker exec -i fabric-postgres psql -U fabricuser fabricdb < $(FILE)
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
# KAFKA MANAGEMENT (Debug & Inspection)
# =============================================================================
# NOTE: Topics auto-initialize via docker-compose.yml kafka-init container
#       These commands are for debugging and inspection only

kafka-topics: ## List all Kafka topics
	@echo "$(YELLOW)📋 Listing Kafka topics...$(NC)"
	@docker exec fabric-kafka kafka-topics --bootstrap-server localhost:9092 --list

kafka-describe: ## Describe a Kafka topic (make kafka-describe TOPIC=user.created)
	@if [ -z "$(TOPIC)" ]; then \
		echo "$(RED)❌ Specify: make kafka-describe TOPIC=topic-name$(NC)"; \
		exit 1; \
	fi
	@docker exec fabric-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic $(TOPIC)

kafka-delete: ## Delete a Kafka topic (make kafka-delete TOPIC=user.created)
	@if [ -z "$(TOPIC)" ]; then \
		echo "$(RED)❌ Specify: make kafka-delete TOPIC=topic-name$(NC)"; \
		exit 1; \
	fi
	@echo "$(RED)⚠️  Deleting topic: $(TOPIC)$(NC)"
	@docker exec fabric-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic $(TOPIC)
	@echo "$(GREEN)✅ Topic deleted: $(TOPIC)$(NC)"

kafka-consumer: ## Consume messages from a topic (make kafka-consumer TOPIC=user.created)
	@if [ -z "$(TOPIC)" ]; then \
		echo "$(RED)❌ Specify: make kafka-consumer TOPIC=topic-name$(NC)"; \
		exit 1; \
	fi
	@echo "$(YELLOW)📨 Consuming from topic: $(TOPIC)$(NC)"
	@docker exec -it fabric-kafka kafka-console-consumer \
		--bootstrap-server localhost:9092 \
		--topic $(TOPIC) \
		--from-beginning

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
