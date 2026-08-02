import { useState, type ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { TextArea } from './fields';
import { ErrorState } from './states';

export interface ConfirmationDialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: 'primary' | 'danger';
  /** Ask for a reason and pass it to onConfirm. Required when `reasonRequired`. */
  reasonLabel?: string;
  reasonRequired?: boolean;
  reasonMinLength?: number;
  pending?: boolean;
  error?: unknown;
  onConfirm: (reason: string) => void;
}

export const ConfirmationDialog = ({
  open,
  onClose,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Cancel',
  tone = 'primary',
  reasonLabel,
  reasonRequired = false,
  reasonMinLength = 0,
  pending = false,
  error,
  onConfirm,
}: ConfirmationDialogProps) => {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  const tooShort = reason.trim().length < reasonMinLength;
  const invalid = reasonRequired && (reason.trim() === '' || tooShort);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      size="sm"
      dismissible={!pending}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={pending}>
            {cancelLabel}
          </Button>
          <Button
            variant={tone === 'danger' ? 'danger' : 'primary'}
            loading={pending}
            onClick={() => {
              setTouched(true);
              if (invalid) return;
              onConfirm(reason.trim());
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {reasonLabel ? (
        <TextArea
          label={reasonLabel}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          onBlur={() => setTouched(true)}
          rows={4}
          required={reasonRequired}
          error={
            touched && invalid
              ? reasonMinLength > 0
                ? `Please give at least ${reasonMinLength} characters of context.`
                : 'A reason is required.'
              : undefined
          }
          hint="The next reviewer sees this. Passing a decision on without context leaves them worse informed than you were."
        />
      ) : null}
      {error ? <ErrorState error={error} compact className="mt-3" /> : null}
    </Modal>
  );
};
