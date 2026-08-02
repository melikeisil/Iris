import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import type { WorkItemRow } from '@/features/work-items/viewModel';
import { EmptyState } from '@/design-system/components/states';

/**
 * The violet "CLARIFICATIONS NEEDED" panel from iris_employee_dashboard_default:
 *   bg-[#111620] border border-primary/30 p-6 rounded-xl, a watermark AI glyph at 10%,
 *   and per-item cards at bg-primary/5 border border-primary/10.
 *
 * Content is real: reported work items whose backend workflow status names an
 * information wait. There is no clarification endpoint, so nothing is fabricated - if
 * no item is waiting, the panel says so.
 */
export const ClarificationsPanel = ({ rows }: { rows: readonly WorkItemRow[] }) => (
  <section className="relative flex-1 overflow-hidden rounded-xl border border-primary/30 bg-field p-6">
    <Sparkles
      aria-hidden
      className="absolute right-4 top-4 size-12 text-primary opacity-10"
      strokeWidth={1}
    />
    <h2 className="mb-4 font-technical-label text-[11px] uppercase tracking-widest text-primary">
      Clarifications needed
    </h2>

    {rows.length === 0 ? (
      <EmptyState
        title="Nothing is waiting on you"
        description="None of the requests you raised are waiting for more information."
        compact
      />
    ) : (
      <ul className="space-y-4">
        {rows.slice(0, 3).map((row) => (
          <li key={row.id}>
            <Link
              to={row.href}
              className="block rounded-lg border border-primary/10 bg-primary/5 p-4 transition-all hover:border-primary/30"
            >
              <div className="mb-2 flex items-center gap-2">
                <span className="font-technical-label text-[10px] text-primary/70">
                  {row.referenceKey}
                </span>
                <span aria-hidden className="size-1 rounded-full bg-primary/30" />
                <span className="text-[10px] text-on-surface-variant">{row.status}</span>
              </div>
              <p className="text-sm leading-snug text-on-surface">{row.title}</p>
              <span className="mt-3 block text-[11px] font-bold uppercase text-primary">
                Answer questions
              </span>
            </Link>
          </li>
        ))}
      </ul>
    )}
  </section>
);
