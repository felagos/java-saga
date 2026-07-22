COMPOSE_FILE := docker-compose.full.yml
COMPOSE      := docker compose -f $(COMPOSE_FILE)

.PHONY: build up down restart logs ps clean infra-up infra-down

build: ## Build the bff image
	$(COMPOSE) build

up: ## Build (if needed) and start MariaDB + bff
	$(COMPOSE) up -d --build

down: ## Stop and remove containers
	$(COMPOSE) down

restart: down up ## Restart everything

logs: ## Follow logs
	$(COMPOSE) logs -f

ps: ## Show container status
	$(COMPOSE) ps

clean: ## Stop containers and remove volumes (wipes MariaDB data)
	$(COMPOSE) down -v

infra-up: ## Start only MariaDB (for running bff locally via ./gradlew bootRun)
	docker compose up -d

infra-down: ## Stop the infra-only stack
	docker compose down
