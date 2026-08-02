import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  AiConfidenceIndicator,
  PriorityBadge,
  RiskBadge,
  StatusBadge,
} from '../badges';

/**
 * Badges carry the backend's enum vocabulary into the UI. The logic worth testing is
 * the mapping - the workflow status is shown verbatim (humanised), never replaced by
 * its category - and the accessible text, since colour alone is not an indicator.
 */
describe('badges', () => {
  it('shows the real workflow status, humanised, with its category in the title', () => {
    render(<StatusBadge status="CODE_REVIEW" statusCategory="REVIEW" />);

    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Code Review');
    expect(badge).toHaveAttribute('title', 'Code Review (Review)');
    expect(badge).toHaveTextContent('Status:');
  });

  it('labels priority for screen readers as well as colour', () => {
    render(<PriorityBadge priority="CRITICAL" />);

    const badge = screen.getByTestId('priority-badge');
    expect(badge).toHaveTextContent('Critical');
    expect(badge).toHaveTextContent('Priority:');
  });

  it('renders nothing when the backend did not assess risk', () => {
    const { container } = render(<RiskBadge risk={null} />);

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId('risk-badge')).not.toBeInTheDocument();
  });

  it('renders an assessed risk level with its word', () => {
    render(<RiskBadge risk="HIGH" />);

    expect(screen.getByTestId('risk-badge')).toHaveTextContent('High risk');
  });

  it('formats a 0..1 confidence as a percentage and a band', () => {
    render(<AiConfidenceIndicator confidence={0.92} />);

    expect(screen.getByTestId('ai-confidence')).toHaveAccessibleName(
      'AI confidence 92 percent, High',
    );
  });

  it('tolerates a 0..100 confidence payload and bands it the same way', () => {
    render(<AiConfidenceIndicator confidence={40} />);

    expect(screen.getByTestId('ai-confidence')).toHaveAccessibleName(
      'AI confidence 40 percent, Low',
    );
  });

  it('says confidence is unavailable rather than showing a zero bar', () => {
    render(<AiConfidenceIndicator confidence={null} />);

    expect(screen.getByTestId('ai-confidence')).toHaveTextContent('Confidence unavailable');
  });
});
