import { useState } from 'react';
import { MessageSquarePlus } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { CheckboxField, TextArea } from '@/design-system/components/fields';
import { ErrorState } from '@/design-system/components/states';

export const CommentComposer = ({
  onSubmit,
  pending,
  error,
  canPostInternal,
}: {
  onSubmit: (input: { body: string; internal: boolean }) => void;
  pending: boolean;
  error: unknown;
  canPostInternal: boolean;
}) => {
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);

  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        if (body.trim() === '') return;
        onSubmit({ body: body.trim(), internal });
        setBody('');
      }}
    >
      <TextArea
        label="Add a comment"
        rows={3}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder="Answer a clarification, record a decision, or leave context for whoever picks this up."
      />
      {canPostInternal ? (
        <CheckboxField
          label="Internal comment"
          hint="Visible only to people who may see internal comments. The requester will not see it."
          checked={internal}
          onChange={(event) => setInternal(event.target.checked)}
        />
      ) : null}
      {error ? <ErrorState error={error} compact /> : null}
      <Button
        type="submit"
        size="sm"
        loading={pending}
        disabled={body.trim() === ''}
        leadingIcon={<MessageSquarePlus className="size-4" />}
      >
        Comment
      </Button>
    </form>
  );
};
