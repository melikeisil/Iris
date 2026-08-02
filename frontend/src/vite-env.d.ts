/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_DEMO_MODE?: string;
  readonly VITE_ORG_NAME?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
