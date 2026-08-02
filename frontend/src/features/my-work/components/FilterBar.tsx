import { ChevronDown, Search } from 'lucide-react';
import { cn } from '@/lib/cn';

/**
 * The filter row from iris_my_work:
 *   pills at bg-[#111620] border border-[#1C2432] px-3 py-1.5 rounded text-xs, plus a
 *   right-aligned search input with a leading glyph.
 */
export interface FilterDefinition {
  id: string;
  label: string;
  value: string;
  options: readonly { value: string; label: string }[];
  onChange: (value: string) => void;
}

export const FilterBar = ({
  filters,
  query,
  onQueryChange,
}: {
  filters: readonly FilterDefinition[];
  query: string;
  onQueryChange: (value: string) => void;
}) => (
  <div className="flex flex-wrap items-center justify-between gap-3">
    <div className="flex flex-wrap items-center gap-2">
      {filters.map((filter) => (
        <div key={filter.id} className="relative">
          <label className="sr-only" htmlFor={`filter-${filter.id}`}>
            {filter.label}
          </label>
          <select
            id={`filter-${filter.id}`}
            value={filter.value}
            onChange={(event) => filter.onChange(event.target.value)}
            className={cn(
              'appearance-none rounded border border-border-field bg-field py-1.5 pl-3 pr-8 text-xs',
              filter.value === '' ? 'text-on-surface-variant' : 'text-on-surface',
            )}
          >
            <option value="">{filter.label}</option>
            {filter.options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <ChevronDown
            aria-hidden
            className="pointer-events-none absolute right-2 top-1/2 size-3.5 -translate-y-1/2 text-on-surface-variant"
          />
        </div>
      ))}
    </div>

    <div className="relative flex items-center">
      <Search
        aria-hidden
        className="pointer-events-none absolute left-3 size-3.5 text-on-surface-variant"
      />
      <label className="sr-only" htmlFor="my-work-search">
        Search your work
      </label>
      <input
        id="my-work-search"
        type="search"
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder="Search"
        className="w-48 rounded-lg border border-border-field bg-field py-1.5 pl-10 pr-4 text-sm focus:border-brand-indigo focus:ring-0"
      />
    </div>
  </div>
);
