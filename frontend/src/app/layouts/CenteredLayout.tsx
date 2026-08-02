import type { MouseEvent } from 'react';
import { Outlet } from 'react-router-dom';
import { brand } from '@/config/brand';
import { IrisMark } from '@/components/shell/IrisMark';

/** Login, invitation acceptance and the first-login setup flow. */
const focusMainContent = () => {
  const main = document.getElementById('main-content');
  if (!(main instanceof HTMLElement)) return;
  main.focus();
};

const handleSkipLink = (event: MouseEvent<HTMLAnchorElement>) => {
  event.preventDefault();
  window.location.hash = 'main-content';
  focusMainContent();
};

export const CenteredLayout = () => (
  <div className="flex min-h-screen flex-col bg-background">
    <a
      href="#main-content"
      onClick={handleSkipLink}
      className="sr-only absolute left-3 top-3 z-50 rounded-lg bg-brand-indigo px-3 py-2 text-body-sm text-white focus:not-sr-only"
    >
      Skip to main content
    </a>
    <header className="flex items-center gap-2.5 px-6 py-5">
      <IrisMark />
      <div>
        <p className="text-headline-md leading-tight text-on-surface">{brand.productName}</p>
        <p className="technical-label">{brand.descriptor}</p>
      </div>
    </header>
    <main
      id="main-content"
      tabIndex={-1}
      className="flex flex-1 items-start justify-center px-4 pb-12 pt-4 sm:items-center sm:pt-0"
    >
      <div className="w-full max-w-2xl">
        <Outlet />
      </div>
    </main>
    <footer className="px-6 py-4 text-center technical-label">
      {brand.organizationName} · {brand.productName}
    </footer>
  </div>
);
