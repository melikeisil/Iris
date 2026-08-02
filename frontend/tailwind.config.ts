import type { Config } from 'tailwindcss';

/**
 * Iris design tokens.
 *
 * Names mirror the `tailwind.config` script block embedded in the Google Stitch
 * `code.html` sources, so a class copied out of the Stitch markup resolves here
 * unchanged. Every colour resolves to a CSS custom property declared in
 * src/design-system/styles/tokens.css as space separated RGB channels, which keeps
 * Tailwind's `<alpha-value>` modifier working (`bg-primary/10`, `border-error/30`).
 *
 * No component may contain a raw hex value.
 */
const channel = (name: string) => `rgb(var(${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        background: channel('--iris-background'),
        surface: {
          DEFAULT: channel('--iris-surface'),
          dim: channel('--iris-surface-dim'),
          bright: channel('--iris-surface-bright'),
          variant: channel('--iris-surface-variant'),
          /* `bg-surface-card` / `border-card` shorthands used by the Stitch pages. */
          card: channel('--iris-card'),
        },
        'surface-variant': channel('--iris-surface-variant'),
        'surface-container-lowest': channel('--iris-surface-container-lowest'),
        'surface-container-low': channel('--iris-surface-container-low'),
        'surface-container': channel('--iris-surface-container'),
        'surface-container-high': channel('--iris-surface-container-high'),
        'surface-container-highest': channel('--iris-surface-container-highest'),

        /* Form controls. Stitch writes these as #111620 / #1C2432 literals. */
        field: channel('--iris-field'),
        card: channel('--iris-card'),

        /* Structural hairlines, named by role rather than by hex. */
        'border-card': channel('--iris-border-card'),
        'border-field': channel('--iris-border-field'),
        'border-structural': channel('--iris-border-structural'),
        outline: {
          DEFAULT: channel('--iris-outline'),
          variant: channel('--iris-outline-variant'),
        },
        'outline-variant': channel('--iris-outline-variant'),

        'on-background': channel('--iris-on-background'),
        'on-surface': {
          DEFAULT: channel('--iris-on-surface'),
          variant: channel('--iris-on-surface-variant'),
          bright: channel('--iris-on-surface-bright'),
        },
        'on-surface-variant': channel('--iris-on-surface-variant'),
        'on-surface-bright': channel('--iris-on-surface-bright'),

        primary: {
          DEFAULT: channel('--iris-primary'),
          container: channel('--iris-primary-container'),
        },
        'primary-container': channel('--iris-primary-container'),
        'on-primary': channel('--iris-on-primary'),
        'on-primary-container': channel('--iris-on-primary-container'),
        'inverse-primary': channel('--iris-inverse-primary'),
        secondary: {
          DEFAULT: channel('--iris-secondary'),
          container: channel('--iris-secondary-container'),
        },
        'secondary-container': channel('--iris-secondary-container'),
        'on-secondary': channel('--iris-on-secondary'),
        'on-secondary-container': channel('--iris-on-secondary-container'),
        tertiary: {
          DEFAULT: channel('--iris-tertiary'),
          container: channel('--iris-tertiary-container'),
        },
        'tertiary-container': channel('--iris-tertiary-container'),
        'on-tertiary': channel('--iris-on-tertiary'),
        'on-tertiary-container': channel('--iris-on-tertiary-container'),
        error: {
          DEFAULT: channel('--iris-error'),
          container: channel('--iris-error-container'),
        },
        'error-container': channel('--iris-error-container'),
        'on-error': channel('--iris-on-error'),
        'on-error-container': channel('--iris-on-error-container'),
        'inverse-surface': channel('--iris-inverse-surface'),
        'inverse-on-surface': channel('--iris-inverse-on-surface'),

        /* Brand accents. Indigo = human action, violet = AI, gold = editorial. */
        'brand-indigo': {
          DEFAULT: channel('--iris-brand-indigo'),
          hover: channel('--iris-brand-indigo-hover'),
        },
        'ai-violet': channel('--iris-ai-violet'),
        editorial: channel('--iris-editorial'),
        'success-teal': channel('--iris-success-teal'),
        'warning-amber': channel('--iris-warning-amber'),
        info: channel('--iris-info'),
      },

      fontFamily: {
        sans: ['var(--iris-font-sans)'],
        mono: ['var(--iris-font-mono)'],
        /* Stitch family aliases, so `font-display-lg` etc. resolve verbatim. */
        'display-lg': ['var(--iris-font-editorial)'],
        'display-sm': ['var(--iris-font-editorial)'],
        'headline-md': ['var(--iris-font-sans)'],
        'body-lg': ['var(--iris-font-sans)'],
        'body-sm': ['var(--iris-font-sans)'],
        'technical-label': ['var(--iris-font-mono)'],
        'technical-sm': ['var(--iris-font-mono)'],
      },

      /*
       * The Stitch config declares these only as font families, never as sizes, so
       * `text-display-lg` was inert in the source HTML. The sizes below come from the
       * `typography` block of iris_enterprise/DESIGN.md, which is the only place they
       * are specified, and match the rendered screen.png captures.
       */
      fontSize: {
        'display-lg': ['3rem', { lineHeight: '1.1', letterSpacing: '-0.02em', fontWeight: '500' }],
        'display-sm': ['2rem', { lineHeight: '1.2', letterSpacing: '-0.01em', fontWeight: '600' }],
        'headline-md': ['1.25rem', { lineHeight: '1.4', letterSpacing: '-0.01em', fontWeight: '600' }],
        'body-lg': ['1rem', { lineHeight: '1.6' }],
        'body-sm': ['0.875rem', { lineHeight: '1.5' }],
        'technical-label': ['0.75rem', { lineHeight: '1', letterSpacing: '0.08em', fontWeight: '500' }],
        'technical-sm': ['0.625rem', { lineHeight: '1', letterSpacing: '0.05em', fontWeight: '500' }],
      },

      borderRadius: {
        DEFAULT: '0.25rem',
        sm: '0.25rem',
        md: '0.375rem',
        lg: '0.5rem',
        xl: '0.75rem',
        '2xl': '1rem',
        full: '9999px',
      },

      spacing: {
        /* 8px base; 24px gutter; 40px page margin. */
        gutter: '1.5rem',
        margin: '2.5rem',
        sidebar: '15.5rem' /* 248px, the expanded sidebar */,
        'sidebar-rail': '5rem' /* 80px, the 1024px collapsed rail */,
      },

      maxWidth: {
        shell: '1440px',
      },

      boxShadow: {
        raised: '0 4px 20px rgb(0 0 0 / 0.5)',
        'card-glow': '0 4px 20px rgb(var(--iris-brand-indigo) / 0.1)',
      },

      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'slide-in-right': {
          from: { transform: 'translateX(16px)', opacity: '0' },
          to: { transform: 'translateX(0)', opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-480px 0' },
          '100%': { backgroundPosition: '480px 0' },
        },
      },
      animation: {
        'fade-in': 'fade-in 160ms ease-out',
        'slide-in-right': 'slide-in-right 180ms ease-out',
        shimmer: 'shimmer 1.4s linear infinite',
      },

      screens: {
        /* tablet 768 is Tailwind's `md`; these name the two larger breakpoints. */
        laptop: '1024px',
        desktop: '1440px',
      },
    },
  },
  plugins: [],
} satisfies Config;
