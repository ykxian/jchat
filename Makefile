COMPOSE ?= docker compose
JAVA_ENV ?= test -f ./scripts/use-jchat-env.sh && . ./scripts/use-jchat-env.sh || true

.PHONY: dev deps test test-backend test-frontend fmt lint clean

dev:
	@echo "Start dependencies with: $(COMPOSE) up -d postgres redis"
	@echo "Backend: cd backend && $(JAVA_ENV) && ./gradlew bootRun"
	@echo "Frontend: cd frontend && npm install && npm run dev"

deps:
	$(COMPOSE) up -d postgres redis

test:
	@echo "Run backend tests: cd backend && ./gradlew test"
	@echo "Run frontend tests: cd frontend && npm test"

test-backend:
	cd backend && $(JAVA_ENV) && ./gradlew test

test-frontend:
	cd frontend && npm test

fmt:
	@echo "Backend format: cd backend && ./gradlew spotlessApply"
	@echo "Frontend format: cd frontend && npm run format"

lint:
	@echo "Backend checks: cd backend && ./gradlew check"
	@echo "Frontend lint: cd frontend && npm run lint"

clean:
	rm -rf backend/build frontend/dist frontend/node_modules
