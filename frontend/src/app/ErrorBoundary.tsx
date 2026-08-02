import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@/design-system/components/Button';
import { brand } from '@/config/brand';

interface State {
  error: Error | null;
}

/**
 * Last line of defence. A render crash should still leave the user with something they
 * can read and act on rather than a white page.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // No tokens or user data are logged here - only the render stack.
    console.error('Unhandled render error', error, info.componentStack);
  }

  render(): ReactNode {
    if (!this.state.error) return this.props.children;

    return (
      <div className="flex min-h-screen items-center justify-center bg-background p-6">
        <div className="max-w-md space-y-4 rounded-md border border-error/40 bg-card p-6 text-center">
          <h1 className="font-display-sm font-medium tracking-tight text-[1.75rem] text-on-surface">Something broke</h1>
          <p className="text-body-sm text-on-surface-variant">
            {brand.productName} hit an unexpected error while rendering this screen. Reloading
            usually clears it. Nothing you had already saved is affected.
          </p>
          <p className="font-technical-label text-technical-label text-on-surface-variant break-words">{this.state.error.message}</p>
          <Button onClick={() => window.location.reload()}>Reload</Button>
        </div>
      </div>
    );
  }
}
