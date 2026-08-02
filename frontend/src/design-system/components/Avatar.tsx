import { cn } from '@/lib/cn';
import { initials } from '@/lib/format';

/**
 * Initials only. No remote avatar URLs: a generated image host would fail offline and
 * leak the user's identity to a third party.
 */
export interface AvatarProps {
  name: string;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
  tone?: 'default' | 'accent';
}

const SIZES = {
  xs: 'size-6 text-[0.625rem]',
  sm: 'size-8 text-[0.6875rem]',
  md: 'size-9 text-[0.75rem]',
  lg: 'size-12 text-body-sm',
} as const;

export const Avatar = ({ name, size = 'md', className, tone = 'default' }: AvatarProps) => (
  <span
    className={cn(
      'inline-flex shrink-0 items-center justify-center rounded-full border font-mono uppercase tracking-tight',
      tone === 'accent'
        ? 'border-brand-indigo/40 bg-brand-indigo/15 text-brand-indigo'
        : 'border-border-card bg-surface-container-low text-on-surface-variant',
      SIZES[size],
      className,
    )}
    aria-hidden
    title={name}
  >
    {initials(name) || '?'}
  </span>
);
