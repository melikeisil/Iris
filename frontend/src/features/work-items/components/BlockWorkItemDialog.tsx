import { useState } from 'react';
import { Modal } from '@/design-system/components/Modal';
import { Button } from '@/design-system/components/Button';
import { FormField, TextArea } from '@/design-system/components/fields';
import { ErrorState } from '@/design-system/components/states';

/**
 * Blocking requires a reason of at least 10 characters (WorkItemDtos.BlockRequest) and
 * optionally a blocking work item, which the backend records as a real DEPENDS_ON link
 * rather than leaving it in free text.
 */
export const BlockWorkItemDialog = ({
  open,
  onClose,
  onConfirm,
  pending,
  error,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: (input: { reason: string; blockedByWorkItemId: string | null }) => void;
  pending: boolean;
  error: unknown;
}) => {
  const [reason, setReason] = useState('');
  const [blockerId, setBlockerId] = useState('');
  const [touched, setTouched] = useState(false);

  const tooShort = reason.trim().length < 10;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Block this work item"
      description="Blocked work shows up in the team's blocked queue and in the blocker's own dependency list."
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={pending}
            onClick={() => {
              setTouched(true);
              if (tooShort) return;
              onConfirm({
                reason: reason.trim(),
                blockedByWorkItemId: blockerId.trim() === '' ? null : blockerId.trim(),
              });
            }}
          >
            Block
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <TextArea
          label="Why is this blocked?"
          required
          rows={4}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          onBlur={() => setTouched(true)}
          error={touched && tooShort ? 'Give at least 10 characters of context.' : undefined}
          hint="Whoever picks this up next reads this first."
        />
        <FormField
          label="Blocked by work item id (optional)"
          value={blockerId}
          onChange={(event) => setBlockerId(event.target.value)}
          hint="Recorded as a real dependency link so it appears in blocked-work investigations."
        />
        {error ? <ErrorState error={error} compact /> : null}
      </div>
    </Modal>
  );
};
