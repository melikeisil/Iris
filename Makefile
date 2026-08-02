.PHONY: help up up-mcp down logs build test unit it clean psql reset demo ai-test ai-lint fe-install fe-dev fe-build fe-test mcp-test

help:
	@echo "SmartWork AI - Phase 10"
	@echo ""
	@echo "  make up       Start Postgres, backend, AI service and frontend"
	@echo "  make up-mcp   Start the full stack plus the optional MCP adapter"
	@echo "  make demo     Run the Phase 10 demo smoke script against the live stack"
	@echo "  make down     Stop everything"
	@echo "  make logs     Tail the backend log"
	@echo "  make build    Build the backend jar"
	@echo "  make test     Backend unit + integration tests (needs Docker)"
	@echo "  make unit     Backend unit tests only (no Docker)"
	@echo "  make ai-test  AI service pytest suite"
	@echo "  make ai-lint  AI service ruff + mypy"
	@echo "  make fe-dev   Frontend dev server on :5173 (proxies /api to :8080)"
	@echo "  make fe-build Frontend production build"
	@echo "  make fe-test  Frontend Vitest suite"
	@echo "  make mcp-test MCP adapter pytest suite"
	@echo "  make psql     Open a psql shell"
	@echo "  make reset    Drop the database volume and start fresh"

up:
	@test -f .env || cp .env.example .env
	docker compose up --build -d
	@echo "Backend:     http://localhost:8080"
	@echo "API docs:    http://localhost:8080/swagger-ui.html"
	@echo "AI service:  http://localhost:8000"
	@echo "AI docs:     http://localhost:8000/docs"
	@echo "Frontend:    http://localhost:5173"

up-mcp:
	@test -f .env || cp .env.example .env
	docker compose --profile mcp up --build -d
	@echo "MCP health:  http://localhost:9000/health"
	@echo "MCP HTTP:    http://localhost:9000/mcp"

down:
	docker compose down

logs:
	docker compose logs -f backend

build:
	cd backend && mvn -B clean package -DskipTests

unit:
	cd backend && mvn -B test

it:
	cd backend && mvn -B verify

test: unit it

demo:
	./scripts/demo_smoke.sh

ai-test:
	cd ai-service && pip install -e ".[dev]" && pytest

ai-lint:
	cd ai-service && ruff check app tests && mypy app

psql:
	docker compose exec postgres psql -U smartwork -d smartwork

reset:
	docker compose down -v
	docker compose up --build -d

clean:
	cd backend && mvn -B clean

fe-install:
	cd frontend && npm install

fe-dev: fe-install
	cd frontend && npm run dev

fe-build: fe-install
	cd frontend && npm run build

fe-test: fe-install
	cd frontend && npm test

mcp-test:
	cd mcp-adapter && python3 -m pip install -e ".[dev]" && pytest
