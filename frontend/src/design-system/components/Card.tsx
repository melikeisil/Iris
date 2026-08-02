import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** AI-owned surfaces get a restrained violet outline instead of a shadow. */
  tone?: 'default' | 'ai' | 'warning' | 'danger' | 'editorial';
  interactive?: boolean;
}

const TONES: Record<NonNullable<CardProps['tone']>, string> = {
  default: 'border-border-card bg-card',
  ai: 'border-ai-violet/30 bg-card ai-glow',
  warning: 'border-warning-amber/35 bg-card',
  danger: 'border-error/40 bg-card',
  editorial: 'border-editorial/30 bg-card',
};

export const Card = ({ tone = 'default', interactive = false, className, ...props }: CardProps) => (
  <div
    className={cn(
      'rounded-md border',
      TONES[tone],
      interactive && 'transition-colors hover:border-border-structural hover:bg-field',
      className,
    )}
    {...props}
  />
);

export interface CardHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  /** Cormorant Garamond, for the few genuinely editorial headings. */
  editorial?: boolean;
  className?: string;
  /** Heading level; sections inside a page should not all be h2. */
  as?: 'h2' | 'h3' | 'h4';
}

export const CardHeader = ({
  title,
  description,
  action,
  editorial = false,
  className,
  as: Heading = 'h3',
}: CardHeaderProps) => (
  <div className={cn('flex items-start justify-between gap-4 border-b border-border-card px-4 py-3', className)}>
    <div className="min-w-0">
      <Heading
        className={cn(
          'truncate',
          editorial ? 'font-display-sm font-medium tracking-tight text-[1.375rem]' : 'text-headline-md text-on-surface',
        )}
      >
        {title}
      </Heading>
      {description ? (
        <p className="mt-0.5 text-body-sm text-outline">{description}</p>
      ) : null}
    </div>
    {action ? <div className="shrink-0">{action}</div> : null}
  </div>
);

export const CardBody = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('p-4', className)} {...props} />
);

export const CardFooter = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('flex items-center gap-2 border-t border-border-card px-4 py-3', className)} {...props} />
);
