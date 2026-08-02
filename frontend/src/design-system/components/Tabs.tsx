import { useId, useRef, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

/**
 * WAI-ARIA tab pattern with roving arrow-key focus. Panels are mounted lazily by the
 * caller, which is what lets Work Item detail load each tab's data from its own
 * endpoint only when the tab is first opened.
 */
export interface TabDefinition {
  id: string;
  label: ReactNode;
  /** A small dot after the label, e.g. "AI & Policy •" when new analysis exists. */
  marker?: boolean;
  disabled?: boolean;
}

export interface TabsProps {
  tabs: readonly TabDefinition[];
  activeId: string;
  onChange: (id: string) => void;
  className?: string;
  children?: ReactNode;
  label: string;
}

export const Tabs = ({ tabs, activeId, onChange, className, children, label }: TabsProps) => {
  const baseId = useId();
  const listRef = useRef<HTMLDivElement>(null);

  const move = (delta: number) => {
    const enabled = tabs.filter((tab) => !tab.disabled);
    const index = enabled.findIndex((tab) => tab.id === activeId);
    const next = enabled[(index + delta + enabled.length) % enabled.length];
    if (!next) return;
    onChange(next.id);
    listRef.current
      ?.querySelector<HTMLButtonElement>(`#${CSS.escape(`${baseId}-${next.id}`)}`)
      ?.focus();
  };

  return (
    <div className={className}>
      <div
        ref={listRef}
        role="tablist"
        aria-label={label}
        className="flex gap-1 overflow-x-auto border-b border-border-card"
        onKeyDown={(event) => {
          if (event.key === 'ArrowRight') {
            event.preventDefault();
            move(1);
          } else if (event.key === 'ArrowLeft') {
            event.preventDefault();
            move(-1);
          }
        }}
      >
        {tabs.map((tab) => {
          const selected = tab.id === activeId;
          return (
            <button
              key={tab.id}
              type="button"
              id={`${baseId}-${tab.id}`}
              role="tab"
              aria-selected={selected}
              aria-controls={`${baseId}-${tab.id}-panel`}
              tabIndex={selected ? 0 : -1}
              disabled={tab.disabled}
              onClick={() => onChange(tab.id)}
              className={cn(
                'relative whitespace-nowrap px-3 py-2 text-body-sm transition-colors',
                selected
                  ? 'text-on-surface after:absolute after:inset-x-2 after:-bottom-px after:h-0.5 after:bg-brand-indigo'
                  : 'text-outline hover:text-on-surface',
                tab.disabled && 'cursor-not-allowed opacity-50',
              )}
            >
              {tab.label}
              {tab.marker ? (
                <span aria-hidden className="ml-1.5 inline-block size-1.5 rounded-full bg-ai-violet" />
              ) : null}
            </button>
          );
        })}
      </div>
      {tabs.map((tab) =>
        tab.id === activeId ? (
          <div
            key={tab.id}
            role="tabpanel"
            id={`${baseId}-${tab.id}-panel`}
            aria-labelledby={`${baseId}-${tab.id}`}
            tabIndex={0}
            className="pt-4 focus-visible:outline-none"
          >
            {children}
          </div>
        ) : null,
      )}
    </div>
  );
};
