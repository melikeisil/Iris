import { defineConfig } from 'vitest/config';
import path from 'node:path';

/**
 * Vitest configuration, kept separate from vite.config.ts.
 *
 * Vitest 2.1.x ships its own nested Vite 5 while the app builds on Vite 6. Putting a
 * `test` block in vite.config.ts makes TypeScript reconcile both copies of Vite's
 * plugin types, which cannot succeed. A dedicated config file is Vitest's supported
 * alternative and keeps the build config free of test concerns.
 *
 * The React plugin is intentionally absent: Vitest transforms TSX with esbuild using
 * the `jsx: react-jsx` setting from tsconfig.app.json, so the automatic runtime works
 * without pulling Vite's plugin types into this file.
 */
export default defineConfig({
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    // Real tests exist again, so an empty run is a configuration failure, not a pass.
    passWithNoTests: false,
  },
});
