import type { Recommendation } from '@/api/types';

/**
 * The AI service returns free-form JSON in `analysis` and `plan`. The backend stores it
 * verbatim and does not narrow it, so the frontend reads it defensively: every accessor
 * returns null rather than throwing, and an unrecognised payload degrades to "no
 * structured analysis available" instead of a blank screen.
 */

const asRecord = (value: unknown): Record<string, unknown> | null =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;

export const readString = (source: Record<string, unknown> | null, ...keys: string[]): string | null => {
  if (!source) return null;
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'string' && value.trim() !== '') return value;
  }
  return null;
};

export const readNumber = (source: Record<string, unknown> | null, ...keys: string[]): number | null => {
  if (!source) return null;
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
      return Number(value);
    }
  }
  return null;
};

export const readStringList = (source: Record<string, unknown> | null, ...keys: string[]): string[] => {
  if (!source) return [];
  for (const key of keys) {
    const value = source[key];
    if (Array.isArray(value)) {
      return value.filter((entry): entry is string => typeof entry === 'string');
    }
  }
  return [];
};

/**
 * One AI clarification question, as the backend stores it verbatim from the AI service's
 * `clarification_questions` (snake_case: com.smartwork.analysis.infrastructure
 * .AiServiceDtos.ClarificationQuestion). `id` is what a submitted answer must echo back
 * as `questionId` so the next analysis can match answers to questions.
 */
export interface AiClarificationQuestion {
  id: string;
  question: string;
  field: string | null;
  required: boolean;
}

const readClarificationQuestions = (source: Record<string, unknown> | null): AiClarificationQuestion[] => {
  if (!source) return [];
  const raw = source.clarification_questions;
  if (!Array.isArray(raw)) return [];
  return raw
    .map((entry) => asRecord(entry))
    .filter((entry): entry is Record<string, unknown> => entry !== null)
    .map((entry) => ({
      id: readString(entry, 'id') ?? '',
      question: readString(entry, 'question') ?? 'The AI requested more information.',
      field: readString(entry, 'field'),
      required: entry.required === true,
    }))
    .filter((question) => question.id !== '');
};

export interface AiAnalysisView {
  summary: string | null;
  classification: string | null;
  recommendedPriority: string | null;
  recommendedRisk: string | null;
  recommendedDepartment: string | null;
  recommendedTeam: string | null;
  requiredSkills: string[];
  nextSteps: string[];
  clarifyingQuestions: string[];
  missingInformation: string[];
  estimatedHours: number | null;
  confidence: number | null;
  provider: string | null;
  isMock: boolean;
  isDemoFallback: boolean;
  failed: boolean;
  errorDetail: string | null;
  /** True when the AI could not proceed without more information from the requester. */
  requiresMoreInformation: boolean;
  /** Structured form of the same signal - one entry per question to answer. */
  clarificationQuestions: AiClarificationQuestion[];
}

export const toAnalysisView = (recommendation: Recommendation | null | undefined): AiAnalysisView | null => {
  if (!recommendation) return null;
  const analysis = asRecord(recommendation.analysis);
  return {
    summary: readString(analysis, 'summary', 'analysisSummary', 'explanation', 'rationale'),
    classification: readString(analysis, 'classification', 'category', 'workItemType'),
    recommendedPriority: readString(analysis, 'recommendedPriority', 'priority'),
    recommendedRisk: readString(analysis, 'recommendedRisk', 'riskLevel', 'risk'),
    recommendedDepartment: readString(analysis, 'departmentCode', 'recommendedDepartment', 'department'),
    recommendedTeam: readString(analysis, 'teamCode', 'recommendedTeam', 'team'),
    requiredSkills: readStringList(analysis, 'requiredSkills', 'skills'),
    nextSteps: readStringList(analysis, 'nextSteps', 'recommendedActions', 'actions'),
    clarifyingQuestions: readStringList(analysis, 'clarifyingQuestions', 'questions', 'clarifications'),
    missingInformation: readStringList(analysis, 'missingInformation', 'missingInfo', 'gaps'),
    estimatedHours: readNumber(analysis, 'estimatedHours', 'effortHours'),
    confidence: recommendation.confidence,
    provider: recommendation.provenance?.provider ?? null,
    isMock: recommendation.provenance?.isMock ?? false,
    isDemoFallback: recommendation.provenance?.provider === 'demo-fallback',
    failed: recommendation.status === 'FAILED',
    errorDetail: recommendation.errorDetail,
    requiresMoreInformation: analysis?.requires_more_information === true,
    clarificationQuestions: readClarificationQuestions(analysis),
  };
};

export const latestRecommendation = (
  history: readonly Recommendation[] | undefined,
): Recommendation | null => history?.[0] ?? null;
