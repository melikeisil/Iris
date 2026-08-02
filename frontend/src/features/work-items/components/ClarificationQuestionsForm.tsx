import { useState, type FormEvent } from 'react';
import { CheckCircle2, Send } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { TextArea } from '@/design-system/components/fields';
import { ErrorState } from '@/design-system/components/states';
import type { AiClarificationQuestion } from '@/lib/aiPayload';
import type { ClarificationAnswerInput } from '@/api/types';

/**
 * Requester-facing form for POST /work-items/{id}/additional-information.
 *
 * Validation is deliberately client-side-only and advisory: a required question left
 * blank is blocked here so the requester gets immediate feedback, but the backend is the
 * actual authority on which questions matter (`AnswerInput.answer` is `@NotBlank` per
 * answer submitted, not per question) - this form never invents a rule the API does not
 * also enforce.
 */
export interface ClarificationQuestionsFormProps {
  questions: AiClarificationQuestion[];
  pending: boolean;
  error: unknown;
  submitted: boolean;
  onSubmit: (answers: ClarificationAnswerInput[]) => void;
}

export const ClarificationQuestionsForm = ({
  questions,
  pending,
  error,
  submitted,
  onSubmit,
}: ClarificationQuestionsFormProps) => {
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [attempted, setAttempted] = useState(false);

  const isBlank = (questionId: string) => (answers[questionId] ?? '').trim() === '';
  const missingRequired = questions.filter((question) => question.required && isBlank(question.id));

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAttempted(true);
    if (missingRequired.length > 0) return;

    const payload: ClarificationAnswerInput[] = questions
      .map((question) => ({
        questionId: question.id,
        field: question.field,
        answer: (answers[question.id] ?? '').trim(),
      }))
      .filter((answer) => answer.answer !== '');

    if (payload.length === 0) return;
    onSubmit(payload);
  };

  if (submitted) {
    return (
      <div
        role="status"
        className="flex items-start gap-3 rounded-md border border-success-teal/40 bg-success-teal/10 p-4"
      >
        <CheckCircle2 aria-hidden className="mt-0.5 size-5 shrink-0 text-success-teal" />
        <div className="space-y-1">
          <p className="text-body-sm font-semibold text-on-surface">Answers submitted</p>
          <p className="text-body-sm text-on-surface-variant">
            This item is back in AI analysis. Re-run analysis to fold your answers into a new
            recommendation.
          </p>
        </div>
      </div>
    );
  }

  return (
    <form className="space-y-5" onSubmit={handleSubmit} aria-label="Answer clarification questions">
      <div className="space-y-4">
        {questions.map((question) => (
          <TextArea
            key={question.id}
            label={question.question}
            required={question.required}
            rows={2}
            value={answers[question.id] ?? ''}
            onChange={(event) =>
              setAnswers((current) => ({ ...current, [question.id]: event.target.value }))
            }
            error={attempted && question.required && isBlank(question.id) ? 'This question needs an answer.' : undefined}
          />
        ))}
      </div>

      {error ? <ErrorState error={error} compact /> : null}

      <Button type="submit" size="sm" loading={pending} leadingIcon={<Send className="size-4" />}>
        Submit answers
      </Button>
    </form>
  );
};
