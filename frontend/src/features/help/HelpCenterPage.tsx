import { useMemo, useState } from 'react';
import { LifeBuoy, Mail, Search, ShieldCheck } from 'lucide-react';
import { PageHeader } from '@/design-system/components/PageHeader';
import { Button } from '@/design-system/components/Button';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { brand } from '@/config/brand';

interface HelpTopic {
  id: string;
  title: string;
  summary: string;
  content: readonly string[];
  tags: readonly string[];
}

const TOPICS: readonly HelpTopic[] = [
  {
    id: 'create-request',
    title: 'Create Request',
    summary: 'Capture a new piece of work with the minimum detail Iris needs to classify it.',
    content: [
      'Use Create Request for new business, service or delivery work that does not already exist in My Work.',
      'A clear summary and concrete business impact help the advisory AI produce a better classification and priority suggestion.',
      'After saving, you can run analysis immediately and then open the work item detail to review routing and policy outcomes.',
    ],
    tags: ['request', 'create', 'intake', 'analysis'],
  },
  {
    id: 'my-work',
    title: 'My Work',
    summary: 'Track assigned work, items you raised and tickets you are watching.',
    content: [
      'Use the filters to narrow by status, type and priority without leaving the page.',
      'Work item rows come from the real backend and link directly to the detail page.',
      'Watching an item keeps it in your personal orbit even when you are not the assignee.',
    ],
    tags: ['my work', 'filters', 'watching', 'assigned'],
  },
  {
    id: 'ai-review',
    title: 'AI Review',
    summary: 'Team leads and managers review backend approval routes and advisory AI guidance.',
    content: [
      'The queue itself is real backend data; if there is nothing to review the page will say so plainly.',
      'Advisory AI never commits changes on its own. Human approval or backend policy determines the final route.',
      'Candidate ranking may show a Demo data badge when no real routing outcome has been cached yet.',
    ],
    tags: ['ai', 'review', 'approvals', 'routing'],
  },
  {
    id: 'roles',
    title: 'Roles and permissions',
    summary: 'What each role can see in the shell and what still depends on item-level backend rules.',
    content: [
      'EMPLOYEE users can create work, view their own work and track items they raised or watch.',
      'TEAM_LEAD users gain the AI Review queue when the backend says they can review it.',
      'MANAGER users gain the Manager Overview and escalation views, but backend authorisation still applies to every action.',
    ],
    tags: ['roles', 'permissions', 'employee', 'team lead', 'manager'],
  },
  {
    id: 'dashboard',
    title: 'Dashboard',
    summary: 'A role-aware landing page that mixes real backend cards with explicitly badged demo gaps.',
    content: [
      'Your dashboard widgets change by role, but the shell itself stays the same across the app.',
      'Cards with a Demo data badge are placeholders for backend capabilities that do not exist yet.',
      'Real work, approvals and escalations continue to come from the live backend even when some surrounding widgets are demo-backed.',
    ],
    tags: ['dashboard', 'widgets', 'demo data'],
  },
];

const FAQS = [
  {
    q: 'Why do some widgets say Demo data?',
    a: 'Those widgets cover capabilities that do not have a backend endpoint yet. Iris labels them directly so you can tell live data from placeholders.',
  },
  {
    q: 'Does AI assign work automatically?',
    a: 'No. The AI output is advisory. Backend policy and human approvals decide assignment and routing.',
  },
  {
    q: 'Why can a page exist but still block me?',
    a: 'Navigation visibility is role-aware, but the backend still performs final authorisation for every request and every workflow action.',
  },
] as const;

const matches = (topic: HelpTopic, query: string) => {
  if (query === '') return true;
  const haystack = [topic.title, topic.summary, ...topic.content, ...topic.tags].join(' ').toLowerCase();
  return haystack.includes(query);
};

export const HelpCenterPage = () => {
  const [query, setQuery] = useState('');
  useDocumentTitle('Help Center');

  const normalized = query.trim().toLowerCase();
  const topics = useMemo(
    () => TOPICS.filter((topic) => matches(topic, normalized)),
    [normalized],
  );

  return (
    <div className="mx-auto w-full max-w-shell p-4 laptop:p-8">
      <PageHeader
        eyebrow="Help center"
        title="Quick guidance for the Iris workspace"
        description="A lightweight guide for the hackathon build: what each screen does, which parts are real, and where to ask for help."
        actions={
          <div className="rounded-xl border border-outline-variant bg-surface-container-low px-4 py-3 text-right">
            <p className="font-technical-label text-technical-label uppercase text-brand-indigo">
              Support
            </p>
            <p className="text-sm text-on-surface-variant">Fast answers for the current demo scope</p>
          </div>
        }
      />

      <section className="mb-8 rounded-2xl border border-outline-variant bg-surface-container-low p-5">
        <label htmlFor="help-search" className="sr-only">
          Search help topics
        </label>
        <div className="relative">
          <Search
            aria-hidden
            className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-on-surface-variant"
          />
          <input
            id="help-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search Create Request, My Work, AI Review, roles…"
            className="h-11 w-full rounded-xl border border-border-field bg-field pl-11 pr-4 text-sm text-on-surface placeholder:text-on-surface-variant/40"
          />
        </div>
        <p className="mt-3 text-xs text-on-surface-variant">
          Showing {topics.length} topic{topics.length === 1 ? '' : 's'} matching your search.
        </p>
      </section>

      <div className="grid grid-cols-1 gap-6 laptop:grid-cols-12">
        <section className="space-y-6 laptop:col-span-8">
          {topics.map((topic) => (
            <article
              key={topic.id}
              className="rounded-2xl border border-outline-variant bg-card p-6"
            >
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <span className="font-technical-label text-technical-label uppercase text-brand-indigo">
                  Topic
                </span>
                {topic.tags.slice(0, 3).map((tag) => (
                  <span
                    key={tag}
                    className="rounded border border-outline-variant bg-surface-variant px-2 py-0.5 text-[10px] uppercase tracking-wide text-on-surface-variant"
                  >
                    {tag}
                  </span>
                ))}
              </div>
              <h2 className="font-display-sm text-display-sm italic text-on-surface">
                {topic.title}
              </h2>
              <p className="mt-2 text-body-lg text-on-surface-variant">{topic.summary}</p>
              <ul className="mt-4 space-y-3 text-sm leading-relaxed text-on-surface-variant">
                {topic.content.map((paragraph) => (
                  <li key={paragraph}>{paragraph}</li>
                ))}
              </ul>
            </article>
          ))}
          {topics.length === 0 ? (
            <div className="rounded-2xl border border-outline-variant bg-card p-6">
              <h2 className="font-display-sm text-display-sm italic text-on-surface">
                No matching topic
              </h2>
              <p className="mt-2 text-sm text-on-surface-variant">
                Try broader words like <code className="rounded bg-surface-variant px-1 py-0.5">review</code>,{' '}
                <code className="rounded bg-surface-variant px-1 py-0.5">request</code> or{' '}
                <code className="rounded bg-surface-variant px-1 py-0.5">manager</code>.
              </p>
            </div>
          ) : null}
        </section>

        <aside className="space-y-6 laptop:col-span-4">
          <section className="rounded-2xl border border-outline-variant bg-surface-container-low p-6">
            <div className="mb-4 flex items-center gap-3">
              <ShieldCheck aria-hidden className="size-5 text-brand-indigo" />
              <h2 className="font-headline-md text-headline-md">Quick FAQs</h2>
            </div>
            <dl className="space-y-4">
              {FAQS.map((entry) => (
                <div key={entry.q}>
                  <dt className="text-sm font-semibold text-on-surface">{entry.q}</dt>
                  <dd className="mt-1 text-sm leading-relaxed text-on-surface-variant">
                    {entry.a}
                  </dd>
                </div>
              ))}
            </dl>
          </section>

          <section className="rounded-2xl border border-outline-variant bg-card p-6">
            <div className="mb-4 flex items-center gap-3">
              <LifeBuoy aria-hidden className="size-5 text-brand-indigo" />
              <h2 className="font-headline-md text-headline-md">Support</h2>
            </div>
            <p className="text-sm leading-relaxed text-on-surface-variant">
              This hackathon build does not have an in-app support backend yet. For now,
              route questions through your team lead or the demo owners.
            </p>
            <div className="mt-4 space-y-3 text-sm">
              <a
                href="mailto:iris-support@novatech.test"
                className="flex items-center gap-2 text-brand-indigo hover:underline"
              >
                <Mail aria-hidden className="size-4" />
                iris-support@novatech.test
              </a>
              <p className="text-on-surface-variant">
                Product: {brand.productName} · Tenant: {brand.organizationName}
              </p>
            </div>
            <div className="mt-5">
              <a href="mailto:iris-support@novatech.test">
                <Button variant="secondary">Email support</Button>
              </a>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
};

export default HelpCenterPage;
