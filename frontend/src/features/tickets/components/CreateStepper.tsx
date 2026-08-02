import { Check } from 'lucide-react';
import { cn } from '@/lib/cn';

/**
 * The horizontal stepper from iris_create_ticket:
 *   a 1px #2A3443 rule behind numbered 24px dots, the active dot filled #6C63FF, with
 *   a tracked-out 10px mono caption underneath.
 *
 * The source shows five steps. "Clarification" is not one of them here: the backend has
 * no clarification-question endpoint, so a step that could never advance would be a lie
 * about the workflow. Documented in the page comment and in docs/phase7-api-gaps.md.
 */
export interface StepperProps {
  steps: readonly string[];
  /** Zero-based. */
  current: number;
}

export const CreateStepper = ({ steps, current }: StepperProps) => (
  <ol
    className="relative mx-auto flex max-w-4xl justify-between"
    aria-label={`Step ${current + 1} of ${steps.length}`}
  >
    <span aria-hidden className="absolute left-0 right-0 top-3 z-0 h-px bg-border-structural" />
    {steps.map((label, index) => {
      const done = index < current;
      const active = index === current;
      return (
        <li key={label} className="relative z-10 flex flex-col items-center gap-2">
          <span
            className={cn(
              'flex size-6 items-center justify-center rounded-full border text-xs font-bold',
              done && 'border-success-teal bg-success-teal text-background',
              active && 'border-brand-indigo bg-brand-indigo text-white',
              !done && !active && 'border-outline bg-surface-variant text-on-surface-variant',
            )}
          >
            {done ? <Check aria-hidden className="size-3.5" /> : index + 1}
          </span>
          <span
            className={cn(
              'whitespace-nowrap font-technical-label text-[10px] uppercase tracking-wider',
              active ? 'text-brand-indigo' : 'text-on-surface-variant',
            )}
          >
            {label}
          </span>
          {active ? <span className="sr-only">(current step)</span> : null}
        </li>
      );
    })}
  </ol>
);
