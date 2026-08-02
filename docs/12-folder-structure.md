# 12 — Folder Structure

## 1. Repository root

```
smartwork-ai/
├── README.md
├── docs/                          ← Phase 1 deliverables (this directory)
├── backend/                       ← Spring Boot modular monolith
├── ai-service/                    ← FastAPI AI analysis
├── mcp-adapter/                   ← MCP server
├── frontend/                      ← React + TypeScript
├── docker/                        ← Dockerfiles, nginx, init scripts
├── .github/workflows/             ← CI
├── docker-compose.yml
├── docker-compose.dev.yml         ← hot reload, exposed ports
├── .env.example
├── .gitignore
└── Makefile                       ← make up / test / seed / lint
```

## 2. Backend

```
backend/
├── pom.xml
├── Dockerfile
├── src/main/java/com/smartwork/
│   ├── SmartWorkApplication.java
│   │
│   ├── common/                    ← shared kernel, no dependencies on other modules
│   │   ├── api/                   ApiError, PageResponse, ProblemDetailAdvice
│   │   ├── domain/                Ids (UUIDv7), ClockProvider, DomainEvent
│   │   ├── config/                JacksonConfig, OpenApiConfig, AsyncConfig, CorsConfig
│   │   └── util/                  Encryption, CorrelationId, StructuredLogging
│   │
│   ├── auth/
│   │   ├── api/                   AuthController, LoginRequest, TokenResponse
│   │   ├── application/           AuthService, TokenService, RefreshTokenService
│   │   ├── domain/                RefreshToken, TokenFamily
│   │   └── infrastructure/        JwtEncoder/Decoder, SecurityConfig,
│   │                              JwtAuthenticationFilter, Argon2PasswordEncoder
│   │
│   ├── identity/
│   │   ├── api/                   UserController, UserResponse, UserSkillRequest
│   │   ├── application/           UserService, SkillService, UserQueryService
│   │   ├── domain/                User, Role, UserRole, Skill, UserSkill, EmployeeLevel
│   │   └── infrastructure/        UserRepository, SkillRepository, SkillAliasResolver
│   │
│   ├── organization/
│   │   ├── api/                   DepartmentController
│   │   ├── application/           DepartmentService, OrganizationContextResolver
│   │   ├── domain/                Organization, Department
│   │   └── infrastructure/        OrganizationFilterAspect, repositories
│   │
│   ├── project/                   Project, ProjectComponent
│   ├── team/                      Team, TeamMember, TeamCapacityService
│   │
│   ├── workitem/
│   │   ├── api/                   WorkItemController, WorkItemLinkController,
│   │   │                          CommentController, AttachmentController,
│   │   │                          CreateWorkItemRequest, WorkItemResponse,
│   │   │                          WorkItemDetailResponse, WorkItemMapper
│   │   ├── application/           WorkItemService, WorkItemQueryService,
│   │   │                          WorkItemLinkService, ProgressService,
│   │   │                          WorkItemAccessEvaluator
│   │   ├── domain/                WorkItem, WorkItemLink, WorkItemSkill,
│   │   │                          WorkItemComment, WorkItemAttachment, Watcher,
│   │   │                          WorkItemType, WorkFamily, OriginType, LinkType,
│   │   │                          Priority, RiskLevel, ProgressCalculator,
│   │   │                          WorkItemValidator, WorkFamilyResolver,
│   │   │                          ReferenceKeyGenerator
│   │   └── infrastructure/        WorkItemRepository, WorkItemQueryBuilder,
│   │                              LinkCycleDetector, DuplicateFinder (pg_trgm)
│   │
│   ├── workflow/
│   │   ├── api/                   TransitionRequest, AvailableTransitionResponse
│   │   ├── application/           WorkflowEngine, TransitionService
│   │   ├── domain/                WorkflowDefinition, WorkflowState,
│   │   │                          WorkflowTransition, TransitionGuard,
│   │   │                          guards/ (10 guard implementations)
│   │   └── infrastructure/        repositories, WorkflowDefinitionCache
│   │
│   ├── process/                   ProcessTemplate, ProcessTemplateTask,
│   │                              ProcessDefinition, ProcessOwner,
│   │                              ProcessInstantiator, TemplateMatcher
│   ├── backlog/                   BacklogService, BacklogOrderingService
│   ├── sprint/                    Sprint, SprintService, SprintCapacityCalculator,
│   │                              SprintSnapshotJob, BurndownService
│   │
│   ├── recommendation/
│   │   ├── api/                   RecommendationController, AnalysisController,
│   │   │                          RecommendationResponse, ApproveRequest
│   │   ├── application/           AnalysisService, PlanGenerationService,
│   │   │                          RecommendationService, ApprovalService,
│   │   │                          StalenessDetector
│   │   ├── domain/                Recommendation, RecommendationCandidate,
│   │   │                          ApprovalRequest, RecommendationStatus
│   │   └── infrastructure/        AiAnalysisClient (WebClient), AiRequestMapper,
│   │                              AiResponseValidator, repositories
│   │
│   ├── assignment/
│   │   ├── api/                   CandidateResponse
│   │   ├── application/           CandidateRankingService, AssignmentService
│   │   ├── domain/                CandidateScorer, ScoreComponents, Assignment,
│   │   │                          WorkloadCalculator, EligibilityFilter,
│   │   │                          ExplanationBuilder
│   │   └── infrastructure/        WorkloadQueryRepository (v_user_workload)
│   │
│   ├── policy/
│   │   ├── api/                   PolicyController (admin), PolicyResponse
│   │   ├── application/           PolicyService, PolicyResolver
│   │   ├── domain/                PolicyEngine, RoutingDecision, ReasonCode,
│   │   │                          AssignmentPolicy, EmployeeLevelPolicy,
│   │   │                          LevelRestrictionEvaluator, ApproverScopeResolver
│   │   └── infrastructure/        repositories
│   │
│   ├── jira/
│   │   ├── api/                   JiraController, JiraWebhookController,
│   │   │                          JiraMappingController, JiraConnectionResponse
│   │   ├── application/           JiraOAuthService, JiraSyncService,
│   │   │                          WebhookProcessor, ReconciliationJob
│   │   ├── domain/                JiraConnection, JiraProjectMapping,
│   │   │                          JiraWorkItemMapping, JiraUserMapping,
│   │   │                          WebhookEvent, JiraMappingResolver
│   │   └── infrastructure/        JiraClient, MockJiraClient, AdfConverter,
│   │                              TokenEncryptionService, JiraOutboxHandler,
│   │                              WebhookSignatureVerifier
│   │
│   ├── reporting/                 ReportingController, ReportingQueryService,
│   │                              TicketMetrics, DevelopmentMetrics,
│   │                              CapacityMetrics, AiMetrics
│   ├── notification/              NotificationController, NotificationService,
│   │                              NotificationPublisher (event listener)
│   ├── audit/                     AuditController (admin read), AuditRecorder,
│   │                              AuditLog, @Audited aspect
│   └── outbox/                    OutboxEvent, OutboxPublisher, OutboxRelay,
│                                  OutboxHandler (interface), DeadLetterService
│
├── src/main/resources/
│   ├── application.yml            base
│   ├── application-dev.yml        local, mock providers
│   ├── application-demo.yml       demo seed data enabled
│   ├── application-prod.yml
│   ├── logback-spring.xml         JSON encoder + secret masking
│   └── db/migration/              V1…V13, R__seed_*
│       └── demo/                  V900__demo_data.sql (demo profile only)
│
└── src/test/java/com/smartwork/
    ├── ModularityTest.java        ApplicationModules.verify()
    ├── ArchitectureTest.java      ArchUnit: no entities in api, no cross-module
    │                              infrastructure access
    ├── <module>/…                 unit tests mirroring main
    ├── integration/               @SpringBootTest + Testcontainers
    │   ├── TicketLifecycleIT, DevelopmentLifecycleIT, TicketToBugIT
    │   ├── PolicyRoutingIT, OutboxRelayIT, JiraWebhookIT
    │   └── AuthorizationMatrixIT
    └── support/                   TestFixtures, PostgresContainer, JiraWireMock
```

## 3. AI service

```
ai-service/
├── pyproject.toml
├── Dockerfile
├── .env.example
├── app/
│   ├── main.py                    FastAPI app, middleware, exception handlers
│   ├── config.py                  pydantic-settings
│   ├── deps.py                    service-token auth dependency
│   ├── api/
│   │   ├── analyze.py             /internal/v1/analyze/ticket | /development
│   │   ├── decompose.py           /internal/v1/decompose
│   │   ├── skills.py              /internal/v1/extract-skills
│   │   └── health.py
│   ├── schemas/
│   │   ├── enums.py               mirrors the Java enums exactly
│   │   ├── requests.py            AnalyzeTicketRequest, AnalyzeDevelopmentRequest,
│   │   │                          DecomposeRequest, OrgContext
│   │   ├── responses.py           AnalysisResponse, SuggestedWorkItem,
│   │   │                          ClarificationQuestion, ProviderMeta
│   │   └── validators.py          cross-field rules, vagueness rejection,
│   │                              dependency-graph checks
│   ├── providers/
│   │   ├── base.py                LLMProvider protocol
│   │   ├── anthropic_provider.py
│   │   ├── mock_provider.py       deterministic, scenario-aware
│   │   ├── echo_provider.py       test-only
│   │   └── factory.py             auto-selection + startup probe
│   ├── services/
│   │   ├── analyzer.py            orchestration + repair loop
│   │   ├── decomposer.py
│   │   ├── sanitizer.py           PII redaction, injection detection
│   │   └── skill_resolver.py      alias mapping, unresolved collection
│   ├── prompts/
│   │   ├── ticket_analysis.j2
│   │   ├── development_analysis.j2
│   │   ├── decomposition.j2
│   │   ├── repair.j2
│   │   └── examples/*.json        few-shot, schema-tested
│   └── observability/
│       ├── logging.py             structured JSON
│       └── metrics.py             Prometheus counters
└── tests/
    ├── test_schemas.py
    ├── test_validators.py         vagueness, cycles, vocabulary
    ├── test_sanitizer.py          PII, injection
    ├── test_repair_loop.py
    ├── test_mock_provider.py      determinism + demo scenarios
    ├── test_no_authorization_fields.py   the structural guarantee
    └── fixtures/
```

## 4. MCP adapter

```
mcp-adapter/
├── pyproject.toml
├── Dockerfile
├── .env.example
├── app/
│   ├── server.py                  MCP server, Streamable HTTP on :9000
│   ├── config.py                  base URL, timeouts — no DB, no Jira, no LLM
│   ├── client.py                  SmartWork REST client, forwards the caller's JWT
│   ├── resources/
│   │   ├── work_item.py           work-item:// · ticket://
│   │   ├── project.py             project://
│   │   ├── workload.py            team:// · department://
│   │   ├── employee.py            employee://
│   │   └── sprint.py              sprint://
│   ├── tools/
│   │   ├── work_items.py          get, analyze, list children, list links
│   │   ├── workload.py            team + department workload
│   │   ├── assignment.py          list candidates, create recommendation
│   │   ├── sprint.py              metrics, summary
│   │   └── investigation.py       investigate_blocked_work
│   ├── prompts/                   six prompt templates
│   └── errors.py                  backend status → MCP error translation
└── tests/
    ├── test_no_db_dependency.py
    ├── test_authorization_passthrough.py
    ├── test_policy_not_bypassed.py
    └── test_error_translation.py
```

## 5. Frontend

```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── Dockerfile
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx                    router + providers
    ├── api/
    │   ├── client.ts              fetch wrapper, auth header, 401 refresh, problem+json
    │   ├── generated/             openapi-typescript output
    │   └── hooks/                 TanStack Query hooks per resource
    │       ├── useWorkItems.ts  useWorkItem.ts  useMyWork.ts
    │       ├── useRecommendations.ts  useTeamWorkload.ts
    │       ├── useSprint.ts  useReports.ts  useJira.ts  useNotifications.ts
    ├── auth/
    │   ├── AuthProvider.tsx       token storage (memory + refresh cookie), me query
    │   ├── RequireAuth.tsx  RequireRole.tsx  RequireCapability.tsx
    ├── components/
    │   ├── ui/                    Button, Card, Badge, Table, Modal, Drawer,
    │   │                          Tabs, Select, Combobox, Toast, Skeleton,
    │   │                          EmptyState, Pagination, Tooltip
    │   ├── workitem/              WorkItemCard, TypeBadge, PriorityBadge,
    │   │                          RiskBadge, StatusChip, ProgressBar,
    │   │                          LinkList, DependencyWarning, TransitionButton
    │   ├── ai/                    ConfidenceIndicator, AiExplanationPanel,
    │   │                          ClarificationQuestions, AnalysisSummary,
    │   │                          StaleBanner, MockProviderBadge
    │   ├── assignment/            CandidateCard, ScoreBreakdown (7 bars),
    │   │                          ExclusionReasonList, RouteBadge
    │   ├── board/                 Board, BoardColumn, DraggableCard
    │   └── charts/                BurndownChart, WorkloadChart, CapacityChart,
    │                              PlannedVsUnplannedChart, AiAcceptanceChart
    ├── layouts/                   AppShell, Sidebar (role-aware), TopBar
    ├── pages/
    │   ├── employee/              MyDashboard, CreateTicket, CreateWorkItem,
    │   │                          MyWork, MyTickets, CurrentSprint,
    │   │                          WorkItemDetail, Notifications
    │   ├── teamlead/              TeamDashboard, DepartmentQueue, AiReviewQueue,
    │   │                          TeamBacklog, SprintPlanning, TeamWorkload,
    │   │                          BlockedWork, OverdueWork
    │   ├── processowner/          ProcessDashboard, OwnedTickets,
    │   │                          OwnedEpicsFeatures, CrossTeamPlans,
    │   │                          Dependencies, WaitingVerification
    │   ├── manager/               ExecutiveDashboard, CriticalWork, Escalations,
    │   │                          SlaRisk, DepartmentCapacity, ProjectRisk,
    │   │                          SprintReports, AiAnalytics, AutomationPolicies
    │   └── admin/                 JiraIntegration, Users, Roles, Departments,
    │                              Teams, Skills, Projects, ProcessTemplates,
    │                              WorkflowProfiles, AssignmentPolicies,
    │                              JiraStatusMapping, JiraPriorityMapping, AuditLogs
    ├── schemas/                   Zod schemas, shared by forms and types
    ├── lib/                       formatters, date utils, workload bands, cn()
    ├── styles/                    tokens.css (design tokens), global.css
    └── test/                      Vitest + Testing Library, MSW handlers
```

## 6. Docker and CI

```
docker/
├── postgres/init.sql              extensions, roles (audit_logs grants)
├── nginx/nginx.conf               optional reverse proxy
└── wait-for-it.sh

.github/workflows/
├── backend.yml                    mvn verify + Testcontainers + ArchUnit + Modulith
├── ai-service.yml                 ruff + mypy + pytest
├── mcp-adapter.yml                ruff + pytest
├── frontend.yml                   eslint + tsc + vitest + build
├── openapi-drift.yml              fails if the committed spec differs
└── security.yml                   dependency-check, pip-audit, npm audit, Trivy
```

## 7. Package-visibility conventions

| Package | Visibility | Rule |
|---|---|---|
| `<module>.api` | public | controllers and DTOs; **may not reference `@Entity` types** (ArchUnit) |
| `<module>.application` | public interfaces only | the sole cross-module read path |
| `<module>.domain` | Spring Modulith internal | entities, value objects, domain services |
| `<module>.infrastructure` | Spring Modulith internal | repositories, external clients |

Cross-module writes go through domain events, not service calls, so a module can be extracted later without unwinding a call graph. `ModularityTest` fails the build on any violation — the boundary is verified, not merely documented.
