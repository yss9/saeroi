# SAEROI Landing Page Design

## Goal

Recreate `screens/landing_page.png` as the root React page with a faithful desktop layout, responsive behavior, and working landing-page interactions while preserving the existing MetaMask integration.

## Visual Source

- Canonical reference: `screens/landing_page.png` at 2560 × 4486 pixels.
- The implementation must reproduce the reference hierarchy, warm ivory and beige palette, blue brand accents, orange prices and subscribe action, generous whitespace, and compact editorial typography.
- The desktop comparison viewport is 1280 pixels wide. The page should preserve the reference proportions while allowing content to reflow below that width.
- Visible photographic content is implemented as individual local raster assets with consistent sustainable-fashion art direction. Icons come from one matching icon library rather than handcrafted SVG or CSS drawings.

## Page Structure

### Header

A white sticky-capable header contains the SAEROI wordmark, four navigation links, search, account, and cart controls. The cart shows a count badge sourced from shared application state. Navigation anchors scroll to the corresponding landing-page sections. The account control keeps the existing wallet connection behavior accessible.

### Hero

A full-width warm-beige field contains a large rounded editorial photograph. The image uses an edge-to-edge crop matching the reference, with the subject and blue architectural accent retaining the same visual balance.

### Featured Brands

The section pairs a compact editorial introduction with four equal brand cards. Cards show restrained logo marks, brand names, and hover/focus feedback. The “모든 브랜드 보기” control scrolls or routes through React Router without introducing an unrequested secondary page.

### Curated Picks

The section pairs editorial copy with five product cards. Each card has a distinct product photograph, wishlist control, Korean product name, uppercase brand label, and orange price. Wishlist state and cart state are maintained by React Context so interactions remain consistent across components.

### Trust Strip

Three equal informational columns communicate verified sourcing, material journey, and QR traceability. Each item uses a real library icon inside a blue circular treatment and keeps the pale-blue panel from the reference.

### Footer

The footer reproduces the wordmark, brand statement, social controls, four navigation columns, newsletter form, legal links, and locale control. Newsletter submission validates the email locally and displays an accessible success state; no backend persistence is added.

## Responsive Behavior

- At desktop widths, content uses the broad editorial grid visible in the source image.
- At tablet widths, brand and product cards wrap while section introductions remain visually distinct.
- At mobile widths, navigation collapses into a menu, sections become single-column, horizontal product overflow is avoided, and all interactive targets remain at least 44 pixels high.
- Images use intentional `object-position` values so focal subjects remain visible at all supported widths.

## Architecture

- `src/app/` owns router and application providers.
- `src/pages/landing/` owns the landing page, its section components, content data, and styles.
- `src/shared/api/` owns the Axios client configuration for later Spring Boot calls; the static landing page does not invent API endpoints.
- `src/features/shop/` owns React Context state for cart and wishlist interactions.
- `src/features/wallet/` remains the owner of ethers.js and MetaMask behavior; landing-page account UI composes the existing wallet capability rather than duplicating it.
- The root route renders the landing page through React Router. Unknown paths return to the landing page without adding extra product screens.

## State and Data Flow

Static brand, product, navigation, and footer content live in typed local data structures. User actions dispatch to a reducer-backed React Context with immutable cart and wishlist updates. Wallet state continues through the existing wallet hook. The Axios client reads the existing validated environment configuration and is available for later backend integration, but no network request is made solely to satisfy the technology list.

## Accessibility and Interaction

- Semantic landmarks, headings, navigation lists, buttons, and forms are used throughout.
- Icon-only controls have Korean accessible labels.
- Keyboard focus is clearly visible and color contrast remains legible.
- The mobile menu exposes its expanded state and can be closed with its button or after selecting a link.
- Wishlist, cart, wallet, and newsletter feedback is visible without relying only on color.

## Testing

- Vitest and Testing Library cover initial rendering, navigation anchors, mobile-menu state, wishlist/cart state, newsletter validation, and the retained wallet entry point.
- Tests exercise real components and user-visible behavior.
- The implementation must pass `npm test`, `npm run lint`, and `npm run build`.
- Visual QA compares a browser capture at the same desktop viewport against `screens/landing_page.png`; all P0, P1, and P2 mismatches are fixed before handoff.

## Non-Goals

- No backend endpoints, authentication system, checkout flow, product-detail pages, or data persistence are added.
- Pinia is not installed because it is a Vue-only state manager; React Context is the approved replacement.
- The reference screenshot is not embedded as a full-page background or used as a non-interactive substitute for the interface.
