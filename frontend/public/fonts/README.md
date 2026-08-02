# Fonts (optional, not shipped)

This directory is intentionally empty. The app does **not** depend on anything here and
produces no failed requests or build warnings without it.

The Stitch designs specify Inter, IBM Plex Mono and Cormorant Garamond. Rather than
declare `@font-face` rules pointing at files that are not committed — which cost a
failed request per face on first paint — those families are simply named first in the
font stacks in `src/design-system/styles/tokens.css`. They render if installed on the
machine; otherwise the declared fallbacks (system-ui / ui-monospace / Georgia) are used
and the layout is unaffected.

## Adding them properly

All three are licensed SIL OFL 1.1, so self-hosting is permitted provided the licence
travels with the files. They are not committed to this repo because redistributing font
binaries is a licensing decision for whoever ships the product, not a build detail.

1. Download the woff2 subsets from the upstream projects (rsms/inter, IBM/plex,
   CatharsisFonts/Cormorant) or Google Fonts, together with each `OFL.txt`.
2. Place them here as:

   ```
   inter-{400,500,600,700}.woff2
   ibm-plex-mono-{400,500}.woff2
   cormorant-garamond-{500,600}.woff2
   OFL-inter.txt  OFL-ibm-plex-mono.txt  OFL-cormorant-garamond.txt
   ```

3. Re-add the matching `@font-face` blocks to `src/design-system/styles/fonts.css` with
   `font-display: swap`. That file documents the exact shape.

A public `<link>` to fonts.googleapis.com is deliberately not used: it fails silently
offline and discloses every user's IP to a third party.
