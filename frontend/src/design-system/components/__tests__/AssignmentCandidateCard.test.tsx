import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AssignmentCandidate } from '@/api/types';
import { AssignmentCandidateCard } from '../AssignmentCandidateCard';

const candidate: AssignmentCandidate = {
  candidateUserId: 'user-9',
  fullName: 'Cem Polat',
  rank: 1,
  totalScore: 91,
  scores: {
    skill: 95,
    capacity: 62,
    team: 88,
    experience: 90,
    component: 96,
    deadline: 84,
    fairness: 70,
  },
  workloadRatio: 0.88,
  explanation: 'Primary maintainer of the auth-service component.',
};

/**
 * The card formats the backend's deterministic ranking. It must never recompute a
 * score: the numbers shown have to match the audit trail exactly.
 */
describe('AssignmentCandidateCard', () => {
  it('shows the backend rank and total score verbatim', () => {
    render(<AssignmentCandidateCard candidate={candidate} recommended />);

    expect(screen.getByText('Rank 1 · Score 91')).toBeInTheDocument();
    expect(screen.getByText(/backend top rank/i)).toBeInTheDocument();
    expect(
      screen.getByText('Primary maintainer of the auth-service component.'),
    ).toBeInTheDocument();
  });

  it('keeps the seven score components collapsed until asked for', async () => {
    const user = userEvent.setup();
    render(<AssignmentCandidateCard candidate={candidate} />);

    const toggle = screen.getByRole('button', { name: /score breakdown/i });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await user.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Skill match')).toBeInTheDocument();
    expect(screen.getByText('Fairness')).toBeInTheDocument();
    expect(screen.getByText('95')).toBeInTheDocument();
  });

  it('offers no selection control when the caller passed no handler', () => {
    render(<AssignmentCandidateCard candidate={candidate} />);

    expect(screen.queryByRole('button', { name: /^select/i })).not.toBeInTheDocument();
  });

  it('hands the whole candidate back when selected', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<AssignmentCandidateCard candidate={candidate} onSelect={onSelect} />);

    await user.click(screen.getByRole('button', { name: 'Select' }));

    expect(onSelect).toHaveBeenCalledWith(candidate);
  });
});
