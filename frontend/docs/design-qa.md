# SAEROI Landing Page Design QA

## Metadata

- source visual truth path: `screens/landing_page.png`
- source pixel dimensions: 2560 × 4486 RGBA PNG
- source CSS-equivalent size: 1280 × 2243 at 2× density
- implementation URL: `http://localhost:4173/`
- implementation screenshot path: unavailable — the Codex in-app Browser exposes captures as in-app evidence artifacts but does not provide a persistent filesystem path
- desktop viewport: 1280 × 900 CSS px, device pixel ratio 1; rendered document height 2246 CSS px
- desktop viewport capture raster: 1031 × 889 pixels after the in-app Browser surface's capture scaling/cropping
- combined comparison capture raster: 1280 × 1121 pixels; source and implementation were each normalized to a 640-pixel comparison column from the same 1280-pixel CSS width
- mobile viewport: 390 × 844 CSS px, device pixel ratio 1; capture raster 375 × 812 pixels after browser chrome/scrollbar exclusion
- state: root route, light theme, logged out/no wallet extension, cart count 2, wishlist initially unpressed, newsletter initially empty
- density normalization: the source was downsampled exactly 0.5 to its 1280 × 2243 CSS-equivalent geometry for `view_image`; the rendered page was captured at a 1280 CSS-pixel viewport and both artifacts were scaled 0.5 in the temporary side-by-side comparison harness

## Evidence

The canonical source and browser implementation were both actually opened before judgment. Direct `view_image` decoding of the 4.9 MB original hit a tool base64-decoder limitation, so an exact 0.5 geometry-normalized derivative was generated from the source and opened with `view_image`. Focused source crops for the header/hero, editorial/product area, and trust/footer area were also opened. The original was independently validated as 2560 × 4486 RGBA PNG.

The Codex in-app Browser opened the implementation at 1280 CSS pixels and 390 CSS pixels. A temporary local comparison harness placed the 1280-pixel source and the live 1280-pixel implementation together in the same browser capture for every full-view judgment. The harness and transient source derivatives were removed after QA. Because CUA does not persist screenshot files, the actual in-app Browser captures in this task transcript are the evidence artifacts; no screenshot path is invented.

### Full-view comparison evidence

- Initial combined capture: source on the left, live implementation on the right, both normalized from a 1280-pixel CSS width. It showed a different hero photograph/subject scale and a shallower hero field.
- Post-fix combined capture: source and live implementation together after the source-backed hero, corrected hero rhythm, and cart-state update. Major composition, section order, image hierarchy, color fields, card grids, trust strip, and footer structure align.
- Source normalized height is 2243 CSS-equivalent pixels; final implementation height is 2246 CSS pixels, a 3-pixel total-height delta.

### Focused region evidence

- Header/hero: source `view_image` crop and final 1280-pixel browser capture. Final hero uses a 2432 × 1040 source-derived raster, begins at y=208 CSS px, and retains the source subject, denim bag, cream wall, blue masonry, crop, radius, and shadow treatment.
- Brands/products: source `view_image` crop and final browser capture at `#featured-brands`. Four equal brand tiles, five distinct product images, wishlist icons, labels, brands, and orange prices are present and aligned.
- Trust/footer: source `view_image` crop and final browser capture around `#trust`. All three Phosphor icons, dividers, pale-blue panel, footer columns, newsletter, legal row, social controls, and locale control are present.
- Mobile: final 390 × 844 browser captures in closed-menu and expanded-menu states. No horizontal content overflow was present (`scrollWidth` 375 within the browser's 375-pixel content area), and a DOM geometry scan found no visible link, button, or input below 44 CSS pixels high after the fix.

## Findings

No actionable P0, P1, or P2 findings remain.

### Resolved blocking findings

- [P1] Hero photograph and focal scale did not match the source.
  - Location: `public/images/landing/hero-upcycled-denim.webp`, `src/pages/landing/components/Hero.tsx`.
  - Evidence: the initial implementation showed a different model pose, shirt neckline, much larger tote, and different blue-wall balance; the source shows the cream-sleeved model and narrower denim tote at the left.
  - Impact: this changed the dominant above-the-fold art direction and visual identity.
  - Fix: replaced the standalone hero WebP with the exact source hero region at 2432 × 1040 and matched its 152:65 aspect ratio.

- [P2] Hero field vertical rhythm was too shallow.
  - Location: `.hero` in `src/pages/landing/landing.css`.
  - Evidence: initial implementation frame top was y=192 and the hero ended near y=801; source frame top is y=208 and the beige field ends near y=857.
  - Impact: every following section started early and the page felt denser than the editorial source.
  - Fix: changed desktop hero padding to 128px vertically. Final frame top is y=208 and the full page is within 3 CSS pixels of the source height.

- [P2] Initial cart badge state differed from the reference.
  - Location: `src/features/shop/ShopContext.tsx`.
  - Evidence: source badge displayed 2 while implementation displayed 0.
  - Impact: the prominent header interaction was not captured in the same state as the reference.
  - Fix: seeded the prototype's reducer-backed mock cart state at 2 and added a regression test confirming it increments to 3.

- [P2] Mobile logo links had undersized tap heights.
  - Location: `.site-header__logo` and `.site-footer__logo` in `src/pages/landing/landing.css`.
  - Evidence: the initial 390-pixel geometry scan measured visible logo-link heights of 25px and 33.3px.
  - Impact: those interactive home links missed the design requirement for 44-pixel-high mobile targets.
  - Fix: added 44px minimum hit areas while preserving their visible alignment. The final scan found no visible interactive element below 44px high.

### Follow-up polish (P3, non-blocking)

- The implementation intentionally localizes the source's English header labels and some footer/trust copy into coherent Korean. This changes text widths and density slightly but preserves hierarchy, meaning, and usability.
- The Windows in-app Browser reserves about 15 CSS pixels for its scrollbar, so content raster width is slightly narrower than the source capture, which appears to use an overlay/hidden scrollbar. This is capture-environment normalization, not page overflow.
- Trust/footer internal heights differ by roughly 15–22 CSS pixels even though the complete page height is within 3 pixels; the resulting visual drift is minor and does not change hierarchy or readability.

## Required Fidelity Surface Review

- Fonts and typography: Korean hierarchy, weights, line height, tracking, heading wraps, compact card text, supplied raster wordmark, and orange price emphasis are visually consistent. No clipping or truncation defects were observed.
- Spacing and layout rhythm: desktop composition, 32px outer margins, editorial left rails, equal brand/product grids, hero whitespace, trust strip, and footer rhythm align after fixes. Mobile becomes a single column without collisions or horizontal product overflow.
- Colors and tokens: ivory paper, warm beige, pale blue, brand blue, orange price/action, muted gray copy, borders, radii, and shadows map coherently to the source.
- Image quality and asset fidelity: all visible photography is real local raster imagery. The blocking hero was replaced with the source-backed raster; product imagery remains sharp and art-direction consistent. Logos are local image assets, not CSS/SVG approximations.
- Copy and content: all standalone text is coherent and section-specific. Localized navigation/trust/footer wording is classified as P3 rather than a usability defect.
- Icons: controls and trust/social marks use one consistent Phosphor icon family with accessible Korean labels on icon-only controls.
- Accessibility: semantic landmarks/headings/forms are present, keyboard focus is visible, wishlist and disclosures expose state, newsletter feedback uses alert/status roles, reduced motion is handled, mobile targets are at least 44px high, and no mobile overflow was found.

## Primary Interaction Verification

- Desktop nav anchor: `브랜드` updated the hash to `#featured-brands` and brought the section to the sticky-header offset.
- Wishlist: first product toggled from `aria-pressed=false` / `찜하기` to `aria-pressed=true` / `찜 해제`.
- Wallet: account button expanded `wallet-popover`; the no-extension state exposed the MetaMask install link and explanatory message.
- Mobile menu: 390px menu changed from collapsed to expanded, exposed all four links, then selecting `신상품` navigated to `#curated-picks`, closed the menu, and restored the collapsed state.
- Newsletter empty: `이메일 주소를 입력해 주세요.` alert.
- Newsletter malformed: `올바른 이메일 주소를 입력해 주세요.` alert.
- Newsletter valid: `구독 신청이 완료되었습니다.` status.
- Browser console: a fresh direct implementation tab reported zero warnings and zero errors before and after the fixes.

## Comparison History

1. Pass 1 — blocked.
   - Findings: P1 hero asset/focal mismatch; P2 hero vertical-rhythm mismatch.
   - Fixes: replaced the hero raster from the canonical source, matched its aspect ratio, and set 128px desktop vertical padding.
   - Post-fix evidence: combined in-app source/implementation capture plus focused final header/hero capture; hero natural size 2432 × 1040, frame top y=208, final document height 2246.

2. Pass 2 — blocked.
   - Findings: P2 reference cart-state mismatch; P2 mobile logo tap heights below 44px.
   - Fixes: seeded reducer-backed cart mock state at 2 with a test-first regression, and enlarged both logo-link hit areas without changing their visible typography.
   - Post-fix evidence: focused header-actions capture and accessible label `장바구니, 상품 2개`; final 390px capture and geometry scan with zero visible controls under 44px high.

3. Pass 3 — passed.
   - Evidence: final combined 1280px source/live-implementation capture, final focused desktop regions, final 390px responsive capture, clean console, and successful interaction checks.
   - Result: no actionable P0/P1/P2 findings remain; only the P3 items listed above remain.

4. Pass 4 — passed (logo asset follow-up).
   - Evidence: the supplied 2172 × 724 transparent PNG is rendered from `/img/logo.png` at its original 3:1 ratio in both home links; focused in-app mobile captures verified the 90px header lockup and 120px footer lockup without distortion or clipping.
   - Result: the approximate-font wordmark P3 is resolved; link names and 44px hit areas remain intact.

## Implementation Checklist

- [x] Source and implementation opened and compared in the same capture.
- [x] Desktop and mobile responsive states captured in the Codex in-app Browser.
- [x] Header, hero, brand grid, product grid, trust strip, and footer inspected at focused scale.
- [x] Nav, wishlist, wallet, mobile menu, and newsletter states tested.
- [x] Browser console checked on a fresh direct tab.
- [x] Every P0/P1/P2 finding fixed and visually rechecked.

final result: passed
