import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { paths } from '@/app/router/paths';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export const NotFoundPage = () => {
  useDocumentTitle('Page not found');
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-6 p-16 text-center">
      <Compass aria-hidden className="size-12 text-on-surface-variant" strokeWidth={1.25} />
      <div className="space-y-2">
        <h1 className="font-display-sm text-display-sm italic text-on-surface">
          That page does not exist
        </h1>
        <p className="text-body-sm text-on-surface-variant">
          The link may be out of date, or the screen may not be part of this release.
        </p>
      </div>
      <Link to={paths.dashboard}>
        <Button>Back to the dashboard</Button>
      </Link>
    </div>
  );
};

export default NotFoundPage;
