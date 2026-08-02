import { useEffect, useState, type ReactNode } from 'react';
import { Bell, MonitorCog, Palette, Shield, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@/design-system/components/PageHeader';
import { Button } from '@/design-system/components/Button';
import { CheckboxField, SelectField } from '@/design-system/components/fields';
import { useAuth } from '@/app/providers/AuthProvider';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { preferencesStore, type UserPreferences } from './preferences';

const Section = ({
  title,
  description,
  icon: Icon,
  children,
}: {
  title: string;
  description: string;
  icon: typeof Bell;
  children: ReactNode;
}) => (
  <section className="rounded-2xl border border-outline-variant bg-card p-6">
    <div className="mb-5 flex items-start gap-3">
      <div className="rounded-xl border border-outline-variant bg-surface-container-low p-2">
        <Icon aria-hidden className="size-5 text-brand-indigo" />
      </div>
      <div>
        <h2 className="font-headline-md text-headline-md text-on-surface">{title}</h2>
        <p className="mt-1 text-sm leading-relaxed text-on-surface-variant">{description}</p>
      </div>
    </div>
    {children}
  </section>
);

export const SettingsPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [preferences, setPreferences] = useState<UserPreferences>(() => preferencesStore.load());
  const [loggingOut, setLoggingOut] = useState(false);
  useDocumentTitle('Settings');

  useEffect(() => {
    preferencesStore.save(preferences);
    document.documentElement.dataset.appearancePreference = preferences.appearance;
  }, [preferences]);

  if (!user) return null;

  return (
    <div className="mx-auto w-full max-w-shell p-4 laptop:p-8">
      <PageHeader
        eyebrow="Settings"
        title="Personal workspace preferences"
        description="Account identity comes from the backend. Notification, appearance and view preferences below are local to this browser unless the backend gains a settings endpoint later."
      />

      <div className="grid grid-cols-1 gap-6 laptop:grid-cols-12">
        <div className="space-y-6 laptop:col-span-7">
          <Section
            title="Profile summary"
            description="Backend-managed account data for the authenticated user."
            icon={Shield}
          >
            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Name
                </dt>
                <dd className="mt-1 text-sm text-on-surface">{user.fullName}</dd>
              </div>
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Email
                </dt>
                <dd className="mt-1 text-sm text-on-surface">{user.email}</dd>
              </div>
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Roles
                </dt>
                <dd className="mt-1 text-sm text-on-surface">{user.roles.join(', ')}</dd>
              </div>
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Team
                </dt>
                <dd className="mt-1 text-sm text-on-surface">
                  {user.teams.map((team) => team.name).join(', ') || 'No team'}
                </dd>
              </div>
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Department
                </dt>
                <dd className="mt-1 text-sm text-on-surface">{user.department?.name ?? '—'}</dd>
              </div>
              <div>
                <dt className="font-technical-label text-technical-label uppercase text-on-surface-variant">
                  Level
                </dt>
                <dd className="mt-1 text-sm text-on-surface">{String(user.employeeLevel)}</dd>
              </div>
            </dl>
            <p className="mt-5 rounded-xl border border-outline-variant bg-surface-container-low px-4 py-3 text-xs text-on-surface-variant">
              Backend-managed identity is read-only here. Password, role and organisation changes need a real backend account-management endpoint before they can safely live in this UI.
            </p>
          </Section>

          <Section
            title="Workspace preferences"
            description="Frontend-only preferences stored in localStorage for this browser."
            icon={Palette}
          >
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
              <SelectField
                label="Appearance"
                value={preferences.appearance}
                onChange={(event) =>
                  setPreferences((current) => ({
                    ...current,
                    appearance: event.target.value as UserPreferences['appearance'],
                  }))
                }
                options={[
                  { value: 'dark', label: 'Dark' },
                  { value: 'system', label: 'Follow system' },
                ]}
                hint="The current hackathon UI is dark-first; this preference is stored now so the app can honour it consistently as themes expand."
              />
              <SelectField
                label="Default My Work view"
                value={preferences.defaultWorkView}
                onChange={(event) =>
                  setPreferences((current) => ({
                    ...current,
                    defaultWorkView: event.target.value as UserPreferences['defaultWorkView'],
                  }))
                }
                options={[
                  { value: 'assigned', label: 'Assigned to me' },
                  { value: 'reported', label: 'Raised by me' },
                  { value: 'watching', label: 'Watching' },
                ]}
                hint="Used only on the frontend for now."
              />
            </div>
          </Section>

          <Section
            title="Notifications"
            description="Local notification preferences for this browser. These do not create backend notification records."
            icon={Bell}
          >
            <div className="space-y-4">
              <CheckboxField
                label="Review alerts"
                hint="Prefer prominent reminders for approvals and review work."
                checked={preferences.reviewAlerts}
                onChange={(event) =>
                  setPreferences((current) => ({
                    ...current,
                    reviewAlerts: event.target.checked,
                  }))
                }
              />
              <CheckboxField
                label="Product digest"
                hint="Keep quick release and workflow hints visible in the UI."
                checked={preferences.productDigest}
                onChange={(event) =>
                  setPreferences((current) => ({
                    ...current,
                    productDigest: event.target.checked,
                  }))
                }
              />
              <CheckboxField
                label="Desktop notifications"
                hint="Stored here as a preference only; no browser permission flow is triggered in this MVP."
                checked={preferences.desktopNotifications}
                onChange={(event) =>
                  setPreferences((current) => ({
                    ...current,
                    desktopNotifications: event.target.checked,
                  }))
                }
              />
            </div>
          </Section>
        </div>

        <div className="space-y-6 laptop:col-span-5">
          <Section
            title="Storage boundary"
            description="A plain explanation of what Iris stores locally today."
            icon={MonitorCog}
          >
            <ul className="space-y-3 text-sm leading-relaxed text-on-surface-variant">
              <li>Local only: appearance, notification toggles and default My Work view.</li>
              <li>Backend-managed: identity, roles, teams and all work-item data.</li>
              <li>Session tokens are already handled by the auth client and are separate from these preferences.</li>
            </ul>
            <p className="mt-4 rounded-xl border border-outline-variant bg-surface-container-low px-4 py-3 text-xs text-on-surface-variant">
              Local storage key: <code className="rounded bg-field px-1 py-0.5">{preferencesStore.key}</code>
            </p>
          </Section>

          <Section
            title="Session"
            description="End the current session safely on this device."
            icon={LogOut}
          >
            <div className="space-y-4">
              <p className="text-sm text-on-surface-variant">
                Signing out clears the local session and returns you to the login screen.
              </p>
              <Button
                variant="danger"
                loading={loggingOut}
                onClick={async () => {
                  setLoggingOut(true);
                  try {
                    await logout();
                    navigate(paths.login, { replace: true });
                  } finally {
                    setLoggingOut(false);
                  }
                }}
              >
                Sign out
              </Button>
            </div>
          </Section>
        </div>
      </div>
    </div>
  );
};

export default SettingsPage;
