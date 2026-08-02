import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
  type Ref,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';
import { cn } from '@/lib/cn';

/**
 * A keyboard-accessible dropdown menu (WAI-ARIA menu button pattern).
 *
 * Arrow keys move through items, Home/End jump, Escape closes and returns focus to the
 * trigger, and an outside click closes. Written by hand rather than pulled in as a
 * dependency because it is 90 lines and the alternative was a headless-UI package this
 * project would otherwise not need.
 */
export interface MenuItemDefinition {
  id: string;
  label: ReactNode;
  icon?: ReactNode;
  onSelect?: () => void;
  href?: string;
  disabled?: boolean;
  destructive?: boolean;
  /** Renders a separator plus a small caption above this item. */
  sectionLabel?: ReactNode;
}

export interface MenuProps {
  trigger: (props: {
    ref: Ref<HTMLButtonElement>;
    onClick: () => void;
    onKeyDown: (event: ReactKeyboardEvent<HTMLButtonElement>) => void;
    'aria-expanded': boolean;
    'aria-haspopup': 'menu';
    'aria-controls': string;
  }) => ReactNode;
  items: readonly MenuItemDefinition[];
  align?: 'start' | 'end';
  className?: string;
  label: string;
}

export const Menu = ({ trigger, items, align = 'end', className, label }: MenuProps) => {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  const selectable = items.filter((item) => !item.disabled);

  const close = useCallback(
    (returnFocus = true) => {
      setOpen(false);
      if (returnFocus) triggerRef.current?.focus();
    },
    [],
  );

  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (!listRef.current?.contains(target) && !triggerRef.current?.contains(target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const items = listRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]');
    items?.[activeIndex]?.focus();
  }, [open, activeIndex]);

  const onListKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((index) => (index + 1) % Math.max(1, selectable.length));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => (index - 1 + selectable.length) % Math.max(1, selectable.length));
    } else if (event.key === 'Home') {
      event.preventDefault();
      setActiveIndex(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      setActiveIndex(Math.max(0, selectable.length - 1));
    }
  };

  return (
    <div className={cn('relative', className)}>
      {trigger({
        ref: triggerRef,
        onClick: () => {
          setActiveIndex(0);
          setOpen((value) => !value);
        },
        onKeyDown: (event) => {
          if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            setActiveIndex(0);
            setOpen(true);
          }
        },
        'aria-expanded': open,
        'aria-haspopup': 'menu',
        'aria-controls': menuId,
      })}

      {open ? (
        <div
          ref={listRef}
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={onListKeyDown}
          className={cn(
            'absolute z-40 mt-1 min-w-56 animate-fade-in rounded-md border border-border-card bg-field py-1 shadow-raised',
            align === 'end' ? 'right-0' : 'left-0',
          )}
        >
          {items.map((item) => (
            <div key={item.id}>
              {item.sectionLabel ? (
                <p className="border-t border-border-card px-3 pb-1 pt-2 technical-label first:border-0">
                  {item.sectionLabel}
                </p>
              ) : null}
              <button
                type="button"
                role="menuitem"
                tabIndex={-1}
                disabled={item.disabled}
                onClick={() => {
                  if (item.disabled) return;
                  item.onSelect?.();
                  close(false);
                }}
                className={cn(
                  'flex w-full items-center gap-2.5 px-3 py-2 text-left text-body-sm transition-colors',
                  item.destructive ? 'text-error hover:bg-error/10' : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface',
                  item.disabled && 'cursor-not-allowed opacity-50',
                )}
              >
                {item.icon ? <span aria-hidden className="shrink-0">{item.icon}</span> : null}
                <span className="min-w-0 flex-1">{item.label}</span>
              </button>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
};
