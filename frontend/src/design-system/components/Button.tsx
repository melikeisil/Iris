import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'ai' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

const VARIANTS: Record<ButtonVariant, string> = {
  // Indigo signals a human-driven action.
  primary:
    'bg-brand-indigo text-white shadow-lg shadow-brand-indigo/10 hover:bg-brand-indigo-hover active:scale-95 disabled:bg-brand-indigo/40 disabled:shadow-none',
  secondary:
    'border border-brand-indigo/70 text-brand-indigo hover:bg-brand-indigo/10 active:bg-brand-indigo/15 disabled:border-border-card disabled:text-outline',
  ghost:
    'border border-border-structural text-on-surface-variant hover:bg-white/5 hover:text-on-surface',
  // Violet is reserved for AI actions, never for ordinary submits.
  ai: 'bg-ai-violet/15 border border-ai-violet/30 text-ai-violet hover:bg-ai-violet/25 disabled:opacity-50',
  danger: 'bg-error/15 text-error border border-error/40 hover:bg-error/25',
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-[0.8125rem] gap-1.5',
  md: 'h-10 px-4 text-body-sm gap-2',
  lg: 'h-11 px-5 text-body-sm gap-2',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  leadingIcon?: ReactNode;
  trailingIcon?: ReactNode;
  fullWidth?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    leadingIcon,
    trailingIcon,
    fullWidth = false,
    className,
    children,
    disabled,
    // An explicit type is mandatory: a button inside a form defaults to submit and
    // silently submits it.
    type = 'button',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled === true || loading}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex items-center justify-center rounded-lg font-medium transition-all',
        'disabled:cursor-not-allowed disabled:opacity-70',
        VARIANTS[variant],
        SIZES[size],
        fullWidth && 'w-full',
        className,
      )}
      {...props}
    >
      {loading ? <Loader2 aria-hidden className="size-4 animate-spin" /> : leadingIcon}
      {children}
      {!loading && trailingIcon}
    </button>
  );
});
