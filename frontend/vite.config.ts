import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// The frontend never talks to the FastAPI AI service directly; every AI call is
// proxied through the Spring Boot backend (docs/07-ai-service-contract.md).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: process.env.BACKEND_ORIGIN ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  // Test configuration lives in vitest.config.ts. Vitest 2.1.x bundles its own Vite 5,
  // so declaring `test` here makes tsc compare this file against two different copies
  // of Vite's types; the separate config file is Vitest's own supported layout.
});
