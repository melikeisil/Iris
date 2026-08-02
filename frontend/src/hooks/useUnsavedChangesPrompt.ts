import { useEffect } from 'react';

/**
 * Warns before a browser navigation loses unsaved form state. In-app navigation is
 * handled by each form's own confirmation, because react-router's blocker API is only
 * available on a data router and this app uses the component router.
 */
export const useUnsavedChangesPrompt = (when: boolean): void => {
  useEffect(() => {
    if (!when) return undefined;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [when]);
};
