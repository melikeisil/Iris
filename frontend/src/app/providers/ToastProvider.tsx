import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { IconButton } from '@/design-system/components/IconButton';
import { cn } from '@/lib/cn';

export type ToastTone = 'success' | 'error' | 'info';

interface Toast {
  id: string;
  tone: ToastTone;
  title: string;
  description?: string;
}

interface ToastContextValue {
  notify: (toast: Omit<Toast, 'id'>) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const ICONS = {
  success: CheckCircle2,
  error: AlertTriangle,
  info: Info,
} as const;

export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const notify = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      setToasts((current) => [...current, { ...toast, id }]);
      window.setTimeout(() => dismiss(id), toast.tone === 'error' ? 8000 : 4500);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {/* aria-live so a confirmation is announced, not merely shown. */}
      <div
        className="pointer-events-none fixed bottom-6 left-1/2 z-[60] flex w-full max-w-sm -translate-x-1/2 flex-col gap-2"
        role="region"
        aria-label="Notifications"
      >
        {toasts.map((toast) => {
          const Icon = ICONS[toast.tone];
          return (
            <div
              key={toast.id}
              role={toast.tone === 'error' ? 'alert' : 'status'}
              aria-live={toast.tone === 'error' ? 'assertive' : 'polite'}
              className={cn(
                'pointer-events-auto flex items-start gap-2.5 rounded-md border bg-field p-3 shadow-raised animate-fade-in',
                toast.tone === 'success' && 'border-success-teal/40',
                toast.tone === 'error' && 'border-error/40',
                toast.tone === 'info' && 'border-border-card',
              )}
            >
              <Icon
                aria-hidden
                className={cn(
                  'mt-0.5 size-4 shrink-0',
                  toast.tone === 'success' && 'text-success-teal',
                  toast.tone === 'error' && 'text-error',
                  toast.tone === 'info' && 'text-info',
                )}
              />
              <div className="min-w-0 flex-1">
                <p className="text-body-sm font-medium text-on-surface">{toast.title}</p>
                {toast.description ? (
                  <p className="mt-0.5 text-[0.8125rem] text-outline">{toast.description}</p>
                ) : null}
              </div>
              <IconButton
                size="sm"
                label="Dismiss notification"
                icon={<X className="size-3.5" />}
                onClick={() => dismiss(toast.id)}
              />
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = (): ToastContextValue => {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used inside <ToastProvider>');
  return context;
};
