package com.smartwork.report.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwork.common.error.ApiException;
import com.smartwork.common.error.ErrorCode;
import com.smartwork.common.security.AuthPrincipal;
import com.smartwork.report.domain.WeeklyReport;
import com.smartwork.report.infrastructure.ReportAiClient;
import com.smartwork.report.infrastructure.ReportOutboxEventRepository;
import com.smartwork.report.infrastructure.WeeklyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportService} — authorization/org-scoping, duplicate-send
 * prevention, and the generate/preview/send/history flow. Collaborators are mocked except
 * {@link ObjectMapper}, which is real: converting {@link ReportMetrics} to and from the
 * persisted JSONB shape is core behavior this test exercises, not plumbing to stub out.
 */
class ReportServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-03T09:00:00Z");

    private ReportMetricsService metricsService;
    private ReportAiClient aiClient;
    private ReportEmailComposer emailComposer;
    private ReportAuditLogService auditLog;
    private WeeklyReportRepository reports;
    private ReportOutboxEventRepository outboxEvents;
    private ReportService service;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        metricsService = mock(ReportMetricsService.class);
        aiClient = mock(ReportAiClient.class);
        emailComposer = mock(ReportEmailComposer.class);
        auditLog = mock(ReportAuditLogService.class);
        reports = mock(WeeklyReportRepository.class);
        outboxEvents = mock(ReportOutboxEventRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new ReportService(
                metricsService, aiClient, emailComposer, auditLog, reports, outboxEvents,
                OBJECT_MAPPER, clock);
    }

    private AuthPrincipal managerScopedTo(UUID departmentId, boolean organizationWide) {
        return new AuthPrincipal(
                USER_ID, ORG_ID, departmentId, Set.of(), Set.of("MANAGER"), "SENIOR", organizationWide);
    }

    private ReportMetrics emptyMetrics() {
        return new ReportMetrics(
                LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3), DEPARTMENT_ID, "Engineering",
                Map.of("DONE", 3L), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private Map<String, Object> metricsAsMap() {
        return OBJECT_MAPPER.convertValue(
                emptyMetrics(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
    }

    @Nested
    @DisplayName("authorization and scoping")
    class AuthorizationAndScoping {

        @Test
        @DisplayName("an employee cannot generate a report")
        void employeeForbidden() {
            AuthPrincipal employee = new AuthPrincipal(
                    USER_ID, ORG_ID, DEPARTMENT_ID, Set.of(), Set.of("EMPLOYEE"), "MID", false);

            assertThatThrownBy(() -> service.generate(employee, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));

            verify(metricsService, never()).compute(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("a department-scoped manager always gets their own department, never a requested one")
        void departmentScopedManagerIgnoresRequestedDepartment() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            UUID otherDepartment = UUID.randomUUID();
            when(metricsService.compute(eq(ORG_ID), eq(DEPARTMENT_ID), isNull(), any(), any()))
                    .thenReturn(emptyMetrics());
            when(metricsService.defaultRecipients(ORG_ID)).thenReturn(List.of("manager@acme.test"));
            when(aiClient.summarize(any())).thenReturn(new ReportAiClient.Summary("summary", List.of(), "mock"));
            when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.generate(manager, otherDepartment);

            verify(metricsService).compute(eq(ORG_ID), eq(DEPARTMENT_ID), isNull(), any(), any());
        }

        @Test
        @DisplayName("an organization-wide manager may request a specific department or the whole org")
        void organizationWideManagerMayRequestAnyDepartment() {
            AuthPrincipal manager = managerScopedTo(null, true);
            when(metricsService.compute(eq(ORG_ID), eq(DEPARTMENT_ID), isNull(), any(), any()))
                    .thenReturn(emptyMetrics());
            when(metricsService.defaultRecipients(ORG_ID)).thenReturn(List.of("manager@acme.test"));
            when(aiClient.summarize(any())).thenReturn(new ReportAiClient.Summary("summary", List.of(), "mock"));
            when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.generate(manager, DEPARTMENT_ID);

            verify(metricsService).compute(eq(ORG_ID), eq(DEPARTMENT_ID), isNull(), any(), any());
        }

        @Test
        @DisplayName("a report from another organization is reported as not found, never leaked")
        void crossOrganizationReportIsNotFound() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            UUID reportId = UUID.randomUUID();
            when(reports.findByOrganizationIdAndId(ORG_ID, reportId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.preview(manager, reportId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("claims the report, enqueues one outbox event, and attaches the edited recipients")
        void sendsSuccessfully() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            WeeklyReport report = new WeeklyReport(
                    ORG_ID, DEPARTMENT_ID, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3),
                    USER_ID, NOW, metricsAsMap(),
                    List.of("default@acme.test"));
            when(reports.findByOrganizationIdAndId(ORG_ID, report.getId())).thenReturn(Optional.of(report));
            when(reports.claimForSending(ORG_ID, report.getId(), USER_ID)).thenReturn(1);
            when(emailComposer.subject(any())).thenReturn("Weekly Report");
            when(emailComposer.htmlBody(any(), any(), any())).thenReturn("<p>body</p>");
            when(outboxEvents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            WeeklyReport result = service.send(manager, report.getId(), List.of("Recipient@Acme.test "));

            verify(outboxEvents, times(1)).save(any());
            assertThat(result.recipients()).containsExactly("Recipient@Acme.test");
        }

        @Test
        @DisplayName("a duplicate send request (already SENDING or SENT) is rejected as a conflict, not resent")
        void duplicateSendIsRejected() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            WeeklyReport report = new WeeklyReport(
                    ORG_ID, DEPARTMENT_ID, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3),
                    USER_ID, NOW, metricsAsMap(),
                    List.of("default@acme.test"));
            when(reports.findByOrganizationIdAndId(ORG_ID, report.getId())).thenReturn(Optional.of(report));
            // The compare-and-swap updated zero rows: someone else already claimed it.
            when(reports.claimForSending(ORG_ID, report.getId(), USER_ID)).thenReturn(0);

            assertThatThrownBy(() -> service.send(manager, report.getId(), List.of("a@acme.test")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));

            verify(outboxEvents, never()).save(any());
        }

        @Test
        @DisplayName("rejects an empty recipient list before ever attempting the compare-and-swap")
        void emptyRecipientsRejected() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            WeeklyReport report = new WeeklyReport(
                    ORG_ID, DEPARTMENT_ID, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3),
                    USER_ID, NOW, metricsAsMap(),
                    List.of("default@acme.test"));
            when(reports.findByOrganizationIdAndId(ORG_ID, report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> service.send(manager, report.getId(), List.of()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));

            verify(reports, never()).claimForSending(any(), any(), any());
        }

        @Test
        @DisplayName("rejects a malformed email address")
        void invalidEmailRejected() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            WeeklyReport report = new WeeklyReport(
                    ORG_ID, DEPARTMENT_ID, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 3),
                    USER_ID, NOW, metricsAsMap(),
                    List.of("default@acme.test"));
            when(reports.findByOrganizationIdAndId(ORG_ID, report.getId())).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> service.send(manager, report.getId(), List.of("not-an-email")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode())
                            .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("falls back to a deterministic summary without failing the whole report when AI is unavailable")
        void aiFailureDoesNotFailGeneration() {
            AuthPrincipal manager = managerScopedTo(DEPARTMENT_ID, false);
            when(metricsService.compute(eq(ORG_ID), eq(DEPARTMENT_ID), isNull(), any(), any()))
                    .thenReturn(emptyMetrics());
            when(metricsService.defaultRecipients(ORG_ID)).thenReturn(List.of("manager@acme.test"));
            // ReportAiClient.summarize() itself never throws - it always falls back internally
            // (see ReportAiClient) - so this stubs the fallback's own return shape.
            when(aiClient.summarize(any())).thenReturn(
                    new ReportAiClient.Summary("metrics-only summary", List.of("Review blockers"), "deterministic-fallback"));
            when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            WeeklyReport result = service.generate(manager, null);

            assertThat(result.aiProvider()).isEqualTo("deterministic-fallback");
            assertThat(result.aiSummary()).isEqualTo("metrics-only summary");
            assertThat(result.status()).isEqualTo(WeeklyReport.Status.DRAFT);
        }
    }
}
