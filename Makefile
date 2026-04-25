COMPOSE ?= docker compose
JAVA_ENV ?= test -f ./scripts/use-jchat-env.sh && . ./scripts/use-jchat-env.sh || true

.PHONY: help dev deps test test-backend test-frontend build-frontend clean

help:
	@echo "Available targets:"
	@echo "  make deps            # start postgres and redis"
	@echo "  make dev             # print local startup commands"
	@echo "  make test            # run backend test + frontend build"
	@echo "  make test-backend    # run backend tests"
	@echo "  make test-frontend   # run frontend typecheck + build"
	@echo "  make build-frontend  # build frontend only"
	@echo "  make clean           # remove local build outputs"

dev:
	@echo "Start dependencies: $(COMPOSE) up -d postgres redis"
	@echo "Backend: cd backend && $(JAVA_ENV) && ./gradlew bootRun"
	@echo "Frontend: cd frontend && npm install && npm run dev"

deps:
	$(COMPOSE) up -d postgres redis

test:
	cd backend && $(JAVA_ENV) && ./gradlew test
	cd frontend && npm run build

test-backend:
	cd backend && $(JAVA_ENV) && ./gradlew test

test-frontend:
	cd frontend && npm run build

build-frontend:
	cd frontend && npm run build

clean:
	rm -rf backend/build frontend/dist
