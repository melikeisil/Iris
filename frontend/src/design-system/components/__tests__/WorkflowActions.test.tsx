import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AvailableTransition } from '@/api/types';
import { WorkflowActions } from '../WorkflowActions';

const transition = (overrides: Partial<AvailableTransition> = {}): AvailableTransition => ({
  code: 'START',
  label: 'Start work',
  toState: 'IN_PROGRESS',
  requiresReason: false,
  enabled: true,
  blockedReasons: [],
  ...overrides,
});

/**
 * The component must render exactly what the backend returned for this caller and this
 * state - it never invents an action, and it never silently hides one the backend
 * explained.
 */
describe('WorkflowActions', () => {
  it('says so plainly when the backend offered no transitions', () => {
    render(<WorkflowActions transitions={[]} onTransition={vi.fn()} />);

    expect(screen.getByTestId('no-transitions')).toHaveTextContent(
      'No workflow actions are available to you in this state.',
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('passes the whole transition back, so the caller posts its code', async () => {
    const onTransition = vi.fn();
    const user = userEvent.setup();
    render(<WorkflowActions transitions={[transition()]} onTransition={onTransition} />);

    await user.click(screen.getByRole('button', { name: 'Start work' }));

    expect(onTransition).toHaveBeenCalledWith(expect.objectContaining({ code: 'START' }));
  });

  it('disables a transition the backend refused and exposes the reason on request', async () => {
    const onTransition = vi.fn();
    const user = userEvent.setup();
    render(
      <WorkflowActions
        transitions={[
          transition({
            code: 'CLOSE',
            label: 'Close',
            enabled: false,
            blockedReasons: ['Open child items remain.'],
          }),
        ]}
        onTransition={onTransition}
      />,
    );

    const button = screen.getByRole('button', { name: 'Close' });
    expect(button).toBeDisabled();
    expect(screen.queryByText('Open child items remain.')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /why is close unavailable/i }));

    expect(screen.getByText('Open child items remain.')).toBeInTheDocument();
    expect(onTransition).not.toHaveBeenCalled();
  });

  it('marks only the in-flight transition as busy', () => {
    render(
      <WorkflowActions
        transitions={[transition(), transition({ code: 'BLOCK', label: 'Block' })]}
        onTransition={vi.fn()}
        pendingCode="BLOCK"
      />,
    );

    expect(screen.getByRole('button', { name: 'Block' })).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: 'Start work' })).not.toHaveAttribute('aria-busy');
  });
});
