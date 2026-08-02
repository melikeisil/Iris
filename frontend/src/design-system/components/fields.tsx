import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { AlertCircle } from 'lucide-react';
import { cn } from '@/lib/cn';

/**
 * Form primitives. Every field wires its label, hint and error to the control with
 * real ids so `aria-describedby` and `aria-invalid` are always correct - validation
 * that is only visible as red text is not accessible validation.
 */

interface FieldShellProps {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  error?: string | undefined;
  required?: boolean;
  className?: string;
  children: (ids: { describedBy: string | undefined; invalid: boolean }) => ReactNode;
  /** AI-populated fields carry a violet marker. */
  aiAssisted?: boolean;
}

export const FieldShell = ({
  id,
  label,
  hint,
  error,
  required,
  className,
  children,
  aiAssisted = false,
}: FieldShellProps) => {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="flex items-center gap-2 technical-label">
        <span>
          {label}
          {required ? <span className="ml-1 text-error" aria-hidden>*</span> : null}
        </span>
        {required ? <span className="sr-only">required</span> : null}
        {aiAssisted ? <span className="text-ai-violet">· AI suggested</span> : null}
      </label>
      {children({ describedBy, invalid: Boolean(error) })}
      {hint ? (
        <p id={hintId} className="text-[0.75rem] leading-snug text-outline">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="flex items-center gap-1 text-[0.75rem] text-error" role="alert">
          <AlertCircle aria-hidden className="size-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
    </div>
  );
};

const CONTROL =
  'w-full rounded-lg border border-outline-variant bg-field px-4 text-body-sm text-on-surface placeholder:text-on-surface-variant/40 transition-colors focus:border-primary focus:ring-1 focus:ring-primary disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-error';

export interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: ReactNode;
  hint?: ReactNode;
  error?: string | undefined;
  aiAssisted?: boolean;
  containerClassName?: string;
}

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(function FormField(
  { label, hint, error, required, aiAssisted, className, containerClassName, ...props },
  ref,
) {
  const id = useId();
  return (
    <FieldShell
      id={id}
      label={label}
      hint={hint}
      error={error}
      required={required}
      aiAssisted={aiAssisted}
      className={containerClassName}
    >
      {({ describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cn(CONTROL, 'h-10', className)}
          {...props}
        />
      )}
    </FieldShell>
  );
});

export const DateField = forwardRef<HTMLInputElement, FormFieldProps>(function DateField(props, ref) {
  return <FormField ref={ref} type="date" {...props} />;
});

export interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: ReactNode;
  hint?: ReactNode;
  error?: string | undefined;
  aiAssisted?: boolean;
  options: readonly { value: string; label: string; disabled?: boolean }[];
  placeholder?: string;
  containerClassName?: string;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, hint, error, required, aiAssisted, options, placeholder, className, containerClassName, ...props },
  ref,
) {
  const id = useId();
  return (
    <FieldShell
      id={id}
      label={label}
      hint={hint}
      error={error}
      required={required}
      aiAssisted={aiAssisted}
      className={containerClassName}
    >
      {({ describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cn(CONTROL, 'h-10', className)}
          {...props}
        >
          {placeholder ? <option value="">{placeholder}</option> : null}
          {options.map((option) => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </FieldShell>
  );
});

export interface TextAreaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: ReactNode;
  hint?: ReactNode;
  error?: string | undefined;
  aiAssisted?: boolean;
  containerClassName?: string;
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { label, hint, error, required, aiAssisted, className, containerClassName, rows = 5, ...props },
  ref,
) {
  const id = useId();
  return (
    <FieldShell
      id={id}
      label={label}
      hint={hint}
      error={error}
      required={required}
      aiAssisted={aiAssisted}
      className={containerClassName}
    >
      {({ describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={id}
          rows={rows}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cn(CONTROL, 'py-2', className)}
          {...props}
        />
      )}
    </FieldShell>
  );
});

export const CheckboxField = forwardRef<HTMLInputElement, Omit<FormFieldProps, 'type'>>(
  function CheckboxField({ label, hint, error, className, containerClassName, ...props }, ref) {
    const id = useId();
    const hintId = `${id}-hint`;
    return (
      <div className={cn('flex items-start gap-2.5', containerClassName)}>
        <input
          ref={ref}
          id={id}
          type="checkbox"
          aria-describedby={hint ? hintId : undefined}
          className={cn(
            'mt-0.5 size-4 shrink-0 rounded-sm border border-border-card bg-field text-brand-indigo',
            className,
          )}
          {...props}
        />
        <div className="space-y-0.5">
          <label htmlFor={id} className="block text-body-sm text-on-surface">
            {label}
          </label>
          {hint ? (
            <p id={hintId} className="text-[0.75rem] text-outline">
              {hint}
            </p>
          ) : null}
          {error ? (
            <p className="text-[0.75rem] text-error" role="alert">
              {error}
            </p>
          ) : null}
        </div>
      </div>
    );
  },
);
