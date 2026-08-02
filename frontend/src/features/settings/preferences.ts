export type AppearancePreference = 'dark' | 'system';
export type DefaultWorkView = 'assigned' | 'reported' | 'watching';

export interface UserPreferences {
  appearance: AppearancePreference;
  reviewAlerts: boolean;
  productDigest: boolean;
  desktopNotifications: boolean;
  defaultWorkView: DefaultWorkView;
}

const STORAGE_KEY = 'iris.user-preferences';

const DEFAULTS: UserPreferences = {
  appearance: 'dark',
  reviewAlerts: true,
  productDigest: true,
  desktopNotifications: false,
  defaultWorkView: 'assigned',
};

const safeStorage = (): Storage | null => {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
};

export const preferencesStore = {
  key: STORAGE_KEY,
  defaults(): UserPreferences {
    return { ...DEFAULTS };
  },
  load(): UserPreferences {
    const storage = safeStorage();
    if (!storage) return preferencesStore.defaults();
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return preferencesStore.defaults();
    try {
      const parsed = JSON.parse(raw) as Partial<UserPreferences>;
      return {
        appearance: parsed.appearance === 'system' ? 'system' : 'dark',
        reviewAlerts: parsed.reviewAlerts ?? DEFAULTS.reviewAlerts,
        productDigest: parsed.productDigest ?? DEFAULTS.productDigest,
        desktopNotifications: parsed.desktopNotifications ?? DEFAULTS.desktopNotifications,
        defaultWorkView:
          parsed.defaultWorkView === 'reported' || parsed.defaultWorkView === 'watching'
            ? parsed.defaultWorkView
            : 'assigned',
      };
    } catch {
      return preferencesStore.defaults();
    }
  },
  save(preferences: UserPreferences): void {
    const storage = safeStorage();
    if (!storage) return;
    storage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  },
};
