import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2, Loader2, Sparkles } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { FormField, SelectField, TextArea, DateField } from '@/design-system/components/fields';
import { ErrorState } from '@/design-system/components/states';
import { AiConfidenceIndicator, PolicyDecisionBadge } from '@/design-system/components/badges';
import { CreateStepper } from './components/CreateStepper';
import { RequestSummaryPanel } from './components/RequestSummaryPanel';
import { ticketSchema, composeDescription, type TicketFormValues } from './ticketSchema';
import { useCreateWorkItem } from '@/features/work-items/hooks/useWorkItems';
import { useAnalyzeWorkItem } from '@/features/work-items/hooks/useAnalysis';
import { useToast } from '@/app/providers/ToastProvider';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { toAnalysisView } from '@/lib/aiPayload';
import { api } from '@/api/client/endpoints';
import type { Priority, Recommendation, RiskLevel, RoutingOutcome, WorkItemDetail } from '@/api/types';

/**
 * Create a request.
 *
 * Converted from iris_create_ticket: the stepper, the 8/4 content grid, the
 * `bg-surface-variant border border-outline rounded-xl p-8` form card with its
 * two-column field grid, and the sticky AI summary panel on the right. The
 * "Requested completion date" field is borrowed from iris_create_work_item.
 *
 * The real flow behind it:
 *   1. POST /work-items                  - creates the item
 *   2. POST /work-items/{id}/analyze     - advisory AI analysis
 *   3. POST /work-items/{id}/route       - policy engine routing & candidate ranking
 */
const STEPS = ['Details', 'AI analysis', 'Submitted'] as const;

const TYPE_OPTIONS = [
  { value: 'REQUEST', label: 'Service request' },
  { value: 'INCIDENT', label: 'Incident' },
] as const;

const IMPACT_OPTIONS = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
] as const;

const DEPARTMENT_OPTIONS = [
  { value: '00000000-0000-7000-8000-00000000a104', label: 'Engineering' },
  { value: '00000000-0000-7000-8000-00000000a102', label: 'Information Technology' },
  { value: '00000000-0000-7000-8000-00000000a101', label: 'Human Resources' },
  { value: '00000000-0000-7000-8000-00000000a103', label: 'Finance' },
] as const;

const TEAM_OPTIONS = [
  { value: '00000000-0000-7000-8000-00000000a202', label: 'Backend Team' },
  { value: '00000000-0000-7000-8000-00000000a203', label: 'Frontend Team' },
  { value: '00000000-0000-7000-8000-00000000a204', label: 'Platform Team' },
  { value: '00000000-0000-7000-8000-00000000a201', label: 'Support Team' },
  { value: '00000000-0000-7000-8000-00000000a205', label: 'HR Operations' },
  { value: '00000000-0000-7000-8000-00000000a206', label: 'Finance Operations' },
] as const;

const RISK_OPTIONS = [
  { value: 'LOW', label: 'Low Risk' },
  { value: 'MEDIUM', label: 'Medium Risk' },
  { value: 'HIGH', label: 'High Risk' },
  { value: 'CRITICAL', label: 'Critical Risk' },
] as const;

const ROUTE_OPTIONS = [
  { value: 'TEAM_LEAD_REVIEW', label: 'Team Lead Review' },
  { value: 'PROCESS_OWNER_REVIEW', label: 'Process Owner Review' },
  { value: 'MANAGER_APPROVAL', label: 'Manager Approval' },
  { value: 'MANUAL_EXCEPTION_REVIEW', label: 'Manual Exception Review' },
] as const;

export const CreateRequestPage = () => {
  const navigate = useNavigate();
  const { notify } = useToast();
  useDocumentTitle('Create request');

  const [step, setStep] = useState(0);
  const [created, setCreated] = useState<WorkItemDetail | null>(null);
  const [recommendation, setRecommendation] = useState<Recommendation | null>(null);
  const [routingOutcome, setRoutingOutcome] = useState<RoutingOutcome | null>(null);
  const [submitError, setSubmitError] = useState<unknown>(null);

  const [aiDecision, setAiDecision] = useState<'approved' | 'rejected' | null>(null);
  const [overrideMode, setOverrideMode] = useState(false);
  const [overrideDepartmentId, setOverrideDepartmentId] = useState('00000000-0000-7000-8000-00000000a104');
  const [overrideTeamId, setOverrideTeamId] = useState('00000000-0000-7000-8000-00000000a202');
  const [overridePriority, setOverridePriority] = useState<Priority>('HIGH');
  const [overrideRiskLevel, setOverrideRiskLevel] = useState<RiskLevel>('HIGH');
  const [overrideRoute, setOverrideRoute] = useState('TEAM_LEAD_REVIEW');
  const [savingOverride, setSavingOverride] = useState(false);

  const handleApproveAi = () => {
    setAiDecision('approved');
    setOverrideMode(false);
  };

  const handleRejectAi = () => {
    setAiDecision('rejected');
    setOverrideMode(true);
  };

  const handleSaveOverride = async () => {
    if (!created) return;
    setSavingOverride(true);
    try {
      const updated = await api.workItems.override(created.id, {
        departmentId: overrideDepartmentId,
        teamId: overrideTeamId,
        priority: overridePriority,
        riskLevel: overrideRiskLevel,
        route: overrideRoute,
      });
      setCreated(updated);
      setAiDecision('approved');
      setOverrideMode(false);
    } catch (err) {
      notify({ tone: 'error', title: 'Failed to save override.' });
    } finally {
      setSavingOverride(false);
    }
  };

  const createWorkItem = useCreateWorkItem();
  const analyze = useAnalyzeWorkItem();

  const form = useForm<TicketFormValues>({
    resolver: zodResolver(ticketSchema),
    defaultValues: {
      summary: '',
      description: '',
      ticketType: 'REQUEST',
      affectedSystem: '',
      businessImpact: 'MEDIUM',
      dueDate: '',
      projectId: '',
    },
  });

  const values = form.watch();

  const onSubmit = form.handleSubmit(async (formValues) => {
    setSubmitError(null);
    try {
      const item = await createWorkItem.mutateAsync({
        workItemType: formValues.ticketType,
        summary: formValues.summary,
        description: composeDescription(formValues),
        priority: formValues.businessImpact,
        dueDate: formValues.dueDate === '' ? null : formValues.dueDate,
      });
      setCreated(item);
      setStep(1);

      // Analysis never fails the request: the backend stores a FAILED recommendation
      // with a reason rather than returning an error, so this reads the status.
      const result = await analyze.mutateAsync(item.id);
      setRecommendation(result);

      // Trigger deterministic policy engine routing so candidate scores are generated
      // and approval requests opened if human validation is required.
      try {
        const routeResult = await api.routing.route(item.id);
        setRoutingOutcome(routeResult);
      } catch (routingErr) {
        console.warn('Routing auto-trigger warning:', routingErr);
      }

      setStep(2);
    } catch (error) {
      setSubmitError(error);
      setStep(0);
    }
  });

  const analysis = toAnalysisView(recommendation);
  const analysisFailed = recommendation?.status === 'FAILED';

  return (
    <div className="mx-auto w-full max-w-shell p-4 laptop:p-8">
      <div className="mb-10 px-0 laptop:px-8">
        <CreateStepper steps={STEPS} current={step} />
      </div>

      <div className="grid grid-cols-1 gap-8 laptop:grid-cols-12">
        <div className="space-y-8 laptop:col-span-8">
          <section className="space-y-4">
            <h1 className="font-display-sm text-display-sm italic text-on-surface">
              Create a request
            </h1>
            <p className="max-w-2xl font-body-lg text-body-lg text-on-surface-variant">
              Describe the request or issue. Iris will analyse the context; the backend policy
              engine decides the routing.
            </p>
          </section>

          {step === 2 && created ? (
            <div className="flex flex-col gap-6 rounded-xl border border-outline-variant bg-surface-variant p-6 laptop:p-8">
              <div className="flex items-center gap-4 border-b border-outline-variant/40 pb-6">
                <span className="flex size-14 shrink-0 items-center justify-center rounded-full border border-success-teal/50 bg-success-teal/20">
                  <CheckCircle2 aria-hidden className="size-8 text-success-teal" />
                </span>
                <div>
                  <h2 className="font-display-sm text-2xl italic">Request submitted</h2>
                  <p className="font-body-md text-on-surface-variant">
                    <span className="font-technical-label font-bold text-primary">{created.referenceKey}</span> has been created with status <span className="font-bold text-on-surface">{created.status}</span>.
                  </p>
                </div>
              </div>

              {analysis && !analysisFailed ? (
                <div className="ai-glow space-y-4 rounded-xl border border-ai-violet/30 bg-card p-6 text-left">
                  <div className="flex items-center justify-between gap-2 border-b border-outline-variant/40 pb-3">
                    <div className="flex items-center gap-2">
                      <Sparkles className="size-5 text-ai-violet" />
                      <h3 className="font-technical-label text-technical-label uppercase text-ai-violet font-bold">
                        AI & Policy Engine Analysis Breakdown
                      </h3>
                    </div>
                    <AiConfidenceIndicator confidence={recommendation?.confidence ?? null} />
                  </div>

                  {analysis.summary ? (
                    <div className="space-y-1">
                      <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                        Analysis Summary
                      </p>
                      <p className="font-display-sm text-base italic leading-tight text-on-surface">
                        “{analysis.summary}”
                      </p>
                    </div>
                  ) : null}

                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 laptop:grid-cols-3 pt-1">
                    <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                      <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                        Suggested Department & Team
                      </p>
                      <p className="text-sm font-semibold text-on-surface">
                        {analysis.recommendedDepartment ?? 'Engineering'}
                        {analysis.recommendedTeam ? ` · ${analysis.recommendedTeam}` : ''}
                      </p>
                    </div>

                    <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                      <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                        Priority & Risk Assessment
                      </p>
                      <div className="flex items-center gap-2 pt-0.5">
                        {analysis.recommendedPriority ? (
                          <PolicyDecisionBadge route={`RECOMMENDED_${analysis.recommendedPriority}`} />
                        ) : null}
                        {analysis.recommendedRisk ? (
                          <span className="text-xs font-semibold text-error">
                            Risk: {analysis.recommendedRisk}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    {routingOutcome?.decision ? (
                      <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                        <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                          Policy Engine Route
                        </p>
                        <div className="pt-0.5">
                          <PolicyDecisionBadge route={routingOutcome.decision.route} />
                        </div>
                      </div>
                    ) : null}
                  </div>

                  {analysis.requiredSkills.length > 0 ? (
                    <div className="space-y-1.5 pt-1">
                      <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                        Required Skills
                      </p>
                      <div className="flex flex-wrap gap-1.5">
                        {analysis.requiredSkills.map((skill) => (
                          <span key={skill} className="rounded-full bg-brand-indigo/15 px-2.5 py-0.5 text-xs font-semibold text-brand-indigo border border-brand-indigo/30">
                            {skill}
                          </span>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {/* Interactive Decision & Override Controls */}
                  <div className="border-t border-outline-variant/40 pt-4 mt-3">
                    {aiDecision === 'approved' ? (
                      <div className="flex items-center gap-2 text-sm font-semibold text-success-teal bg-success-teal/10 p-3 rounded-lg border border-success-teal/30">
                        <CheckCircle2 className="size-5 shrink-0 text-success-teal" />
                        <span>Saved</span>
                      </div>
                    ) : overrideMode ? (
                      <div className="space-y-4 rounded-lg border border-error/30 bg-error/5 p-4 text-left">
                        <p className="font-technical-label text-xs font-bold uppercase text-error">
                          Override Department, Team, Priority & Policy Route (AI Rejected)
                        </p>
                        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 laptop:grid-cols-3">
                          <SelectField
                            label="Department"
                            options={DEPARTMENT_OPTIONS}
                            value={overrideDepartmentId}
                            onChange={(e) => setOverrideDepartmentId(e.target.value)}
                          />
                          <SelectField
                            label="Team"
                            options={TEAM_OPTIONS}
                            value={overrideTeamId}
                            onChange={(e) => setOverrideTeamId(e.target.value)}
                          />
                          <SelectField
                            label="Priority"
                            options={IMPACT_OPTIONS}
                            value={overridePriority}
                            onChange={(e) => setOverridePriority(e.target.value as Priority)}
                          />
                          <SelectField
                            label="Risk Level"
                            options={RISK_OPTIONS}
                            value={overrideRiskLevel}
                            onChange={(e) => setOverrideRiskLevel(e.target.value as RiskLevel)}
                          />
                          <SelectField
                            label="Policy Route"
                            options={ROUTE_OPTIONS}
                            value={overrideRoute}
                            onChange={(e) => setOverrideRoute(e.target.value)}
                          />
                        </div>
                        <div className="flex gap-2 justify-end pt-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setOverrideMode(false);
                              setAiDecision(null);
                            }}
                          >
                            Cancel
                          </Button>
                          <Button
                            size="sm"
                            loading={savingOverride}
                            onClick={handleSaveOverride}
                          >
                            Save & Route Override
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-wrap items-center justify-between gap-3 bg-surface-variant/30 p-3 rounded-lg border border-outline-variant/30">
                        <span className="text-xs font-medium text-on-surface-variant">
                          Do you accept the AI recommendation or would you like to override it?
                        </span>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            onClick={handleApproveAi}
                          >
                            ✓ Approve & Accept Recommendation
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            className="border-error/40 text-error hover:bg-error/10 hover:text-error"
                            onClick={handleRejectAi}
                          >
                            ✕ Reject & Override
                          </Button>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ) : null}

              <div className="flex w-full flex-col gap-3 sm:flex-row pt-2">
                <Button
                  size="lg"
                  className="flex-1"
                  onClick={() => navigate(paths.aiReview)}
                >
                  Go to AI Review Queue
                </Button>
                <Button
                  variant="secondary"
                  size="lg"
                  className="flex-1"
                  onClick={() => navigate(paths.workItem(created.id))}
                >
                  View Request Details
                </Button>
                <Button
                  variant="ghost"
                  size="lg"
                  className="flex-1"
                  onClick={() => navigate(paths.dashboard)}
                >
                  Return to Dashboard
                </Button>
              </div>
            </div>
          ) : step === 1 ? (
            <div className="flex flex-col items-center gap-6 rounded-xl border border-ai-violet/30 bg-surface-variant p-12">
              <Loader2 aria-hidden className="size-16 animate-spin text-ai-violet" />
              <p className="font-display-sm text-display-sm italic" role="status">
                Analysing request…
              </p>
              <p className="max-w-sm text-center text-body-sm text-on-surface-variant">
                {created?.referenceKey} was created. Iris is producing an advisory analysis; the
                request is already open either way.
              </p>
            </div>
          ) : (
            <form
              onSubmit={onSubmit}
              noValidate
              className="space-y-6 rounded-xl border border-outline-variant bg-surface-variant p-6 laptop:p-8"
            >
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <FormField
                  label="Title"
                  required
                  containerClassName="md:col-span-2"
                  placeholder="e.g. Production database performance degradation"
                  error={form.formState.errors.summary?.message}
                  {...form.register('summary')}
                />
                <TextArea
                  label="Description"
                  required
                  rows={5}
                  containerClassName="md:col-span-2"
                  placeholder="Detailed explanation of the issue or request…"
                  hint="Include what you expected, what happened, and anything you already tried."
                  error={form.formState.errors.description?.message}
                  {...form.register('description')}
                />
                <SelectField
                  label="Request type"
                  required
                  options={TYPE_OPTIONS}
                  error={form.formState.errors.ticketType?.message}
                  {...form.register('ticketType')}
                />
                <FormField
                  label="Affected system"
                  placeholder="e.g. Core banking database"
                  error={form.formState.errors.affectedSystem?.message}
                  {...form.register('affectedSystem')}
                />
                <SelectField
                  label="Business impact"
                  options={IMPACT_OPTIONS}
                  hint="Priority, risk and routing are recommended after analysis and validated by backend policy."
                  error={form.formState.errors.businessImpact?.message}
                  {...form.register('businessImpact')}
                />
                <DateField
                  label="Requested completion date"
                  error={form.formState.errors.dueDate?.message}
                  {...form.register('dueDate')}
                />
              </div>

              {submitError ? <ErrorState error={submitError} compact /> : null}

              <div className="flex flex-wrap items-center justify-between gap-4 border-t border-outline-variant pt-6">
                <Button variant="ghost" onClick={() => navigate(-1)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  size="lg"
                  loading={form.formState.isSubmitting}
                  trailingIcon={<Sparkles className="size-5" aria-hidden />}
                >
                  Save and analyse
                </Button>
              </div>
            </form>
          )}

          {analysis && !analysisFailed && step === 2 ? (
            <section className="ai-glow space-y-4 rounded-xl border border-ai-violet/30 bg-card p-6">
              <div className="flex items-center justify-between gap-2 border-b border-outline-variant/40 pb-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="size-5 text-ai-violet" />
                  <h2 className="font-technical-label text-technical-label uppercase text-ai-violet font-bold">
                    Advisory AI Analysis & Policy Decision
                  </h2>
                </div>
                <AiConfidenceIndicator confidence={recommendation?.confidence ?? null} />
              </div>

              {analysis.summary ? (
                <div className="space-y-1">
                  <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                    Analysis Summary
                  </p>
                  <p className="font-display-sm text-base italic leading-tight text-on-surface">
                    “{analysis.summary}”
                  </p>
                </div>
              ) : null}

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 laptop:grid-cols-3 pt-1">
                {analysis.recommendedDepartment || analysis.recommendedTeam ? (
                  <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                    <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                      Suggested Department & Team
                    </p>
                    <p className="text-sm font-semibold text-on-surface">
                      {analysis.recommendedDepartment ?? 'Engineering'}
                      {analysis.recommendedTeam ? ` · ${analysis.recommendedTeam}` : ''}
                    </p>
                  </div>
                ) : null}

                {analysis.recommendedPriority || analysis.recommendedRisk ? (
                  <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                    <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                      Priority & Risk Assessment
                    </p>
                    <div className="flex items-center gap-2 pt-0.5">
                      {analysis.recommendedPriority ? (
                        <PolicyDecisionBadge route={`RECOMMENDED_${analysis.recommendedPriority}`} />
                      ) : null}
                      {analysis.recommendedRisk ? (
                        <span className="text-xs font-semibold text-error">
                          Risk: {analysis.recommendedRisk}
                        </span>
                      ) : null}
                    </div>
                  </div>
                ) : null}

                {routingOutcome?.decision ? (
                  <div className="space-y-1 rounded-lg bg-surface-variant/40 p-3 border border-outline-variant/30">
                    <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                      Backend Policy Route
                    </p>
                    <div className="pt-0.5">
                      <PolicyDecisionBadge route={routingOutcome.decision.route} />
                    </div>
                  </div>
                ) : null}
              </div>

              {analysis.requiredSkills.length > 0 ? (
                <div className="space-y-1.5 pt-1">
                  <p className="font-technical-label text-[10px] font-bold uppercase tracking-widest text-on-surface-variant">
                    Required Skills
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {analysis.requiredSkills.map((skill) => (
                      <span key={skill} className="rounded-full bg-brand-indigo/15 px-2.5 py-0.5 text-xs font-semibold text-brand-indigo border border-brand-indigo/30">
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>
              ) : null}

              {analysis.clarificationQuestions.length > 0 ? (
                <div className="space-y-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4">
                  <p className="font-technical-label text-xs font-bold uppercase tracking-wider text-amber-500">
                    AI Clarification Questions (Additional Information Required)
                  </p>
                  <ul className="list-disc list-inside space-y-1 text-xs font-medium text-on-surface">
                    {analysis.clarificationQuestions.map((q) => (
                      <li key={q.id}>{q.question}</li>
                    ))}
                  </ul>
                </div>
              ) : null}

              <p className="text-[10px] italic text-on-surface-variant pt-1">
                AI recommendation only. The backend policy engine makes the routing decision.
              </p>
            </section>
          ) : null}
        </div>

        <div className="laptop:col-span-4">
          <RequestSummaryPanel
            title={values.summary === '' ? 'Untitled request' : values.summary}
            ticketType={
              TYPE_OPTIONS.find((option) => option.value === values.ticketType)?.label ??
              'Not selected'
            }
            currentStep={`${step + 1}. ${STEPS[step]}`}
            savedState={created ? `Saved as ${created.referenceKey}` : 'Not saved'}
            analysisState={
              analysisFailed
                ? 'Unavailable'
                : recommendation
                  ? 'Completed'
                  : step === 1
                    ? 'Running'
                    : 'Not started'
            }
            policyRoute={created ? 'Evaluated by backend' : 'Not evaluated'}
          />
        </div>
      </div>
    </div>
  );
};

export default CreateRequestPage;
