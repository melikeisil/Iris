/**
 * Every user-visible name lives here. Renaming the product or the demo tenant is a
 * one-file change; no component may hardcode "Iris" or "NovaTech".
 */
export const brand = {
  productName: 'Iris',
  descriptor: 'SmartWork AI orchestration platform',
  /** The AI persona referenced in copy, e.g. "Iris analysed this ticket". */
  assistantName: 'Iris',
  organizationName: import.meta.env.VITE_ORG_NAME ?? 'NovaTech',
  supportUrl: '/help',
  documentationUrl: '/help/docs',
  /** Appended to every document title. */
  titleSuffix: 'Iris',
} as const;

export type Brand = typeof brand;

export const pageTitle = (page?: string): string =>
  page ? `${page} - ${brand.titleSuffix}` : brand.titleSuffix;
