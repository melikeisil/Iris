import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';

/**
 * Test environment shims.
 *
 * No MSW server is started here any more: the MVP conversion replaced every screen, so
 * the previous request handlers described endpoints and shapes the current pages no
 * longer call. Add handlers back alongside the tests that need them - a shared mock
 * server nobody's tests exercise is worse than none.
 */
afterEach(() => {
  window.localStorage.clear();
});

// jsdom implements neither of these and several components rely on them.
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  });
}

if (!window.HTMLElement.prototype.scrollIntoView) {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
}

if (typeof globalThis.CSS === 'undefined') {
  Object.defineProperty(globalThis, 'CSS', {
    writable: true,
    value: { escape: (value: string) => value.replace(/([^\w-])/g, '\\$1') },
  });
} else if (!globalThis.CSS.escape) {
  globalThis.CSS.escape = (value: string) => value.replace(/([^\w-])/g, '\\$1');
}
