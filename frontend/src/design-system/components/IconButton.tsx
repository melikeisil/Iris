import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Required: an icon-only control is invisible to a screen reader without it. */
  label: string;
  icon: ReactNode;
  size?: 'sm' | 'md';
  tone?: 'default' | 'danger';
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { label, icon, size = 'md', tone = 'default', className, type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      className={cn(
        'inline-flex items-center justify-center rounded transition-colors',
        'text-outline hover:bg-surface-container-low hover:text-on-surface',
        'disabled:cursor-not-allowed disabled:opacity-50',
        tone === 'danger' && 'hover:text-error',
        size === 'sm' ? 'size-8' : 'size-9',
        className,
      )}
      {...props}
    >
      <span aria-hidden>{icon}</span>
    </button>
  );
});
