import { z } from 'zod';

/**
 * Mirrors the backend's own validation (WorkItemDtos.CreateWorkItemRequest): summary
 * 5..255, description up to 20000. Client-side validation is a courtesy; the backend
 * validates independently and its field errors are surfaced on the same inputs.
 */
export const ticketSchema = z.object({
  summary: z
    .string()
    .trim()
    .min(5, 'Give the ticket a title of at least 5 characters.')
    .max(255, 'Keep the title under 255 characters.'),
  description: z
    .string()
    .trim()
    .min(20, 'Describe what happened, what you expected, and how to reproduce it.')
    .max(20000),
  ticketType: z.enum(['REQUEST', 'INCIDENT']),
  affectedSystem: z.string().max(120).optional().or(z.literal('')),
  businessImpact: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  dueDate: z.string().optional().or(z.literal('')),
  projectId: z.string().optional().or(z.literal('')),
});

export type TicketFormValues = z.infer<typeof ticketSchema>;

/**
 * The backend work item has no `affectedSystem` or `businessImpact` column, so the two
 * are folded into the description as a labelled block rather than dropped. The AI
 * service reads the description, so the context is not lost, and the choice is
 * documented in docs/phase7-api-gaps.md as a candidate for real columns.
 */
export const composeDescription = (values: TicketFormValues): string => {
  const parts = [values.description.trim()];
  if (values.affectedSystem && values.affectedSystem.trim() !== '') {
    parts.push(`\n\nAffected system: ${values.affectedSystem.trim()}`);
  }
  parts.push(`\nReported business impact: ${values.businessImpact}`);
  return parts.join('');
};
