import { cn } from '@/lib/cn';

/**
 * The Iris mark.
 *
 * Served from public/iris-logo.png, copied out of the Stitch design source. The Stitch
 * HTML referenced it from lh3.googleusercontent.com; a remote logo would break an
 * offline demo and leak a request to a third party on every page load, so it is a
 * local asset.
 */
export const IrisMark = ({ className }: { className?: string }) => (
  <img
    src="/iris-logo.png"
    alt="Iris"
    width={32}
    height={32}
    className={cn('h-8 w-8 shrink-0 bg-transparent object-contain', className)}
  />
);
