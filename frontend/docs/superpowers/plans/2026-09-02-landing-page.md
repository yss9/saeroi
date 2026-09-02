# SAEROI Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the responsive SAEROI root landing page shown in `screens/landing_page.png` with working navigation, shopping state, newsletter feedback, and retained MetaMask access.

**Architecture:** React Router renders a single typed landing-page route beneath application providers. A reducer-backed React Context owns wishlist and cart state, the existing wallet feature remains isolated, and landing content is split into focused section components with one page stylesheet. Axios is configured once for future Spring Boot communication without inventing network traffic for this static page.

**Tech Stack:** React 19, TypeScript 6, Vite 8, React Router, React Context, Axios, ethers.js 6, Phosphor Icons, Vitest, Testing Library

**Spec:** `docs/superpowers/specs/2026-09-02-landing-page-design.md`

## Global Constraints

- Canonical visual reference is `screens/landing_page.png` at 2560 × 4486 pixels.
- Desktop visual comparison runs at 1280 pixels wide.
- Pinia is not installed; the user approved React Context as the React-compatible replacement.
- Existing `src/features/wallet/` behavior and tests must remain intact.
- Do not add backend endpoints, authentication, checkout, product-detail routes, or persistence.
- Do not embed the reference as a full-page background.
- Use real raster files for photographic content and Phosphor Icons for interface icons.
- All new user-visible interactions need keyboard focus and Korean accessible labels.

---

### Task 1: Application foundation and test harness

**Files:**
- Modify: `package.json`
- Modify: `package-lock.json`
- Modify: `vite.config.ts`
- Modify: `src/App.tsx`
- Create: `src/test/setup.ts`
- Create: `src/app/router.tsx`
- Create: `src/shared/api/client.ts`
- Test: `src/app/router.test.tsx`

**Interfaces:**
- Consumes: existing `env.apiBaseUrl` from `src/config/env.ts`
- Produces: `router: ReturnType<typeof createBrowserRouter>` and `apiClient: AxiosInstance`

- [ ] **Step 1: Install runtime and test dependencies**

Run:

```powershell
npm install react-router-dom axios @phosphor-icons/react
npm install --save-dev @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom
```

Expected: `package.json` and `package-lock.json` contain the new packages and npm exits 0.

- [ ] **Step 2: Configure the DOM test environment**

Add `test.environment: 'jsdom'` and `test.setupFiles: ['./src/test/setup.ts']` to `vite.config.ts`. Create `src/test/setup.ts`:

```ts
import '@testing-library/jest-dom/vitest'
```

- [ ] **Step 3: Write the failing root-route test**

Create `src/app/router.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { routes } from './router.tsx'

describe('application router', () => {
  it('renders the SAEROI landing page at the root route', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })
    render(<RouterProvider router={router} />)

    expect(screen.getByRole('heading', { name: '지속 가능한 가치를 만드는 브랜드' })).toBeInTheDocument()
  })
})
```

Production change caught: removing or miswiring the `/` landing-page route makes the visible heading disappear.

- [ ] **Step 4: Run the test and verify RED**

Run: `npm test -- src/app/router.test.tsx`

Expected: FAIL because `routes` and the landing-page route do not exist.

- [ ] **Step 5: Add the minimum router and API client**

Create `src/shared/api/client.ts`:

```ts
import axios from 'axios'
import { env } from '../../config/env.ts'

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
})
```

Create `src/app/router.tsx` with an exported `routes` array and `router`. Initially use a small `LandingPage` component that renders the required heading; Task 3 replaces the shell with the finished page.

```tsx
import { createBrowserRouter, type RouteObject } from 'react-router-dom'

function LandingPageShell() {
  return <h1>지속 가능한 가치를 만드는 브랜드</h1>
}

export const routes: RouteObject[] = [
  { path: '/', element: <LandingPageShell /> },
  { path: '*', element: <LandingPageShell /> },
]

export const router = createBrowserRouter(routes)
```

Replace `src/App.tsx` with:

```tsx
import { RouterProvider } from 'react-router-dom'
import { router } from './app/router.tsx'

export default function App() {
  return <RouterProvider router={router} />
}
```

- [ ] **Step 6: Run the focused and existing tests**

Run: `npm test -- src/app/router.test.tsx src/config/env.test.ts src/features/wallet/metamask.test.ts`

Expected: PASS with no warnings.

- [ ] **Step 7: Commit the foundation**

```powershell
git add package.json package-lock.json vite.config.ts src/App.tsx src/test/setup.ts src/app/router.tsx src/app/router.test.tsx src/shared/api/client.ts
git commit -m "chore: add landing page foundation"
```

---

### Task 2: Shared shopping state

**Files:**
- Create: `src/features/shop/ShopContext.tsx`
- Test: `src/features/shop/ShopContext.test.tsx`

**Interfaces:**
- Consumes: string product IDs from landing content
- Produces: `ShopProvider`, `useShop()`, and `ShopContextValue` with `cartCount`, `wishlist`, `toggleWishlist(productId)`, and `addToCart(productId)`

- [ ] **Step 1: Write the failing provider behavior test**

Create `src/features/shop/ShopContext.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ShopProvider, useShop } from './ShopContext.tsx'

function Probe() {
  const shop = useShop()
  return (
    <>
      <output aria-label="장바구니 개수">{shop.cartCount}</output>
      <output aria-label="찜 상태">{String(shop.wishlist.has('denim-bag'))}</output>
      <button onClick={() => shop.toggleWishlist('denim-bag')}>찜</button>
      <button onClick={() => shop.addToCart('denim-bag')}>담기</button>
    </>
  )
}

describe('ShopProvider', () => {
  it('shares wishlist and cart updates with consumers', async () => {
    const user = userEvent.setup()
    render(<ShopProvider><Probe /></ShopProvider>)

    await user.click(screen.getByRole('button', { name: '찜' }))
    await user.click(screen.getByRole('button', { name: '담기' }))

    expect(screen.getByLabelText('찜 상태')).toHaveTextContent('true')
    expect(screen.getByLabelText('장바구니 개수')).toHaveTextContent('1')
  })
})
```

Production change caught: a missing reducer update or provider value leaves the visible state unchanged.

- [ ] **Step 2: Run the test and verify RED**

Run: `npm test -- src/features/shop/ShopContext.test.tsx`

Expected: FAIL because `ShopContext.tsx` does not exist.

- [ ] **Step 3: Implement the minimum reducer-backed context**

Create `src/features/shop/ShopContext.tsx` with immutable `Set` updates and a numeric cart count. `useShop()` throws `useShop must be used within ShopProvider` outside the provider. Keep the public value exactly:

```ts
export interface ShopContextValue {
  cartCount: number
  wishlist: ReadonlySet<string>
  toggleWishlist: (productId: string) => void
  addToCart: (productId: string) => void
}
```

Use `useReducer` actions `{ type: 'toggle-wishlist'; productId: string }` and `{ type: 'add-to-cart'; productId: string }`. Wrap callbacks and the context value with `useCallback` and `useMemo`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `npm test -- src/features/shop/ShopContext.test.tsx`

Expected: PASS.

- [ ] **Step 5: Commit shopping state**

```powershell
git add src/features/shop/ShopContext.tsx src/features/shop/ShopContext.test.tsx
git commit -m "feat: add shared shopping state"
```

---

### Task 3: Landing-page semantics and interactions

**Files:**
- Create: `src/pages/landing/content.ts`
- Create: `src/pages/landing/LandingPage.tsx`
- Create: `src/pages/landing/components/Header.tsx`
- Create: `src/pages/landing/components/Hero.tsx`
- Create: `src/pages/landing/components/FeaturedBrands.tsx`
- Create: `src/pages/landing/components/CuratedPicks.tsx`
- Create: `src/pages/landing/components/TrustStrip.tsx`
- Create: `src/pages/landing/components/Footer.tsx`
- Modify: `src/app/router.tsx`
- Test: `src/pages/landing/LandingPage.test.tsx`

**Interfaces:**
- Consumes: `ShopProvider`, `useShop`, `WalletButton`, local `/images/landing/*` assets
- Produces: `LandingPage` and typed `Brand`, `Product`, `TrustItem`, and footer navigation data

- [ ] **Step 1: Define typed content with literal reference copy**

In `content.ts`, export four brands and five products in the exact visible order:

```ts
export interface Product {
  id: string
  name: string
  brand: string
  price: string
  image: string
  imagePosition: string
}

export const products: Product[] = [
  { id: 'denim-circle-bag', name: '데님 서클 백', brand: 'REWEAVE', price: '129,000원', image: '/images/landing/product-denim-circle.webp', imagePosition: '50% 50%' },
  { id: 'patchwork-work-jacket', name: '패치워크 워크 자켓', brand: 'RE:CODE', price: '149,000원', image: '/images/landing/product-work-jacket.webp', imagePosition: '50% 35%' },
  { id: 'saddle-sling-bag', name: '새들 슬링 백', brand: 'DUSTY MILLER', price: '99,000원', image: '/images/landing/product-saddle-bag.webp', imagePosition: '50% 50%' },
  { id: 'patchwork-bucket-hat', name: '패치워크 버킷 햇', brand: 'OCEAN LOOP', price: '59,000원', image: '/images/landing/product-bucket-hat.webp', imagePosition: '50% 30%' },
  { id: 'market-tote', name: '마켓 토트', brand: 'RE:CODE', price: '79,000원', image: '/images/landing/product-market-tote.webp', imagePosition: '50% 45%' },
]
```

Brand order: `RE:CODE`, `DUSTY MILLER`, `ReWeave`, `OCEAN LOOP`. Trust titles: `검증된 출처`, `소재의 여정`, `QR 추적`.

- [ ] **Step 2: Write failing user-journey tests**

Create `LandingPage.test.tsx` and render the page inside `MemoryRouter` and `ShopProvider`. Cover these separate behaviors:

```tsx
it('renders the reference sections and five curated products', () => {
  renderLandingPage()
  expect(screen.getByRole('heading', { name: '지속 가능한 가치를 만드는 브랜드' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '당신만을 위한 업사이클링 픽' })).toBeInTheDocument()
  expect(screen.getAllByRole('article', { name: /상품/ })).toHaveLength(5)
})

it('toggles a product wishlist state from its accessible button', async () => {
  const user = userEvent.setup()
  renderLandingPage()
  const button = screen.getByRole('button', { name: '데님 서클 백 찜하기' })
  await user.click(button)
  expect(screen.getByRole('button', { name: '데님 서클 백 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
})

it('opens and closes the mobile navigation menu', async () => {
  const user = userEvent.setup()
  renderLandingPage()
  const menu = screen.getByRole('button', { name: '메뉴 열기' })
  await user.click(menu)
  expect(menu).toHaveAttribute('aria-expanded', 'true')
  await user.click(screen.getByRole('button', { name: '메뉴 닫기' }))
  expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute('aria-expanded', 'false')
})

it('validates and accepts a newsletter email', async () => {
  const user = userEvent.setup()
  renderLandingPage()
  await user.click(screen.getByRole('button', { name: '구독하기' }))
  expect(screen.getByRole('alert')).toHaveTextContent('이메일 주소를 입력해 주세요.')
  await user.type(screen.getByLabelText('이메일 주소'), 'hello@saeroi.kr')
  await user.click(screen.getByRole('button', { name: '구독하기' }))
  expect(screen.getByRole('status')).toHaveTextContent('구독 신청이 완료되었습니다.')
})
```

Production changes caught: missing sections, broken wishlist dispatch, menu state regression, or newsletter validation regression.

- [ ] **Step 3: Run the landing tests and verify RED**

Run: `npm test -- src/pages/landing/LandingPage.test.tsx`

Expected: FAIL because the landing components do not exist.

- [ ] **Step 4: Implement semantic section components**

Build each component with semantic HTML and Phosphor icons:

- `Header`: desktop anchor navigation; search, wallet popover, cart badge; mobile menu button with `aria-expanded`.
- `Hero`: one `<section>` and one hero `<img>` with descriptive Korean alt text.
- `FeaturedBrands`: editorial heading and four brand cards.
- `CuratedPicks`: five labeled `<article>` elements and `aria-pressed` wishlist buttons.
- `TrustStrip`: three informational items with `SealCheck`, `SuitcaseSimple`, and `QrCode` icons.
- `Footer`: navigation groups and controlled newsletter form with empty/invalid validation and success status.
- `LandingPage`: composes all sections in source order.

Wrap the route element with `ShopProvider` in `src/app/router.tsx` and keep the wildcard route pointing to the same provider-wrapped landing page.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run: `npm test -- src/pages/landing/LandingPage.test.tsx src/features/shop/ShopContext.test.tsx src/app/router.test.tsx`

Expected: PASS.

- [ ] **Step 6: Commit semantic landing behavior**

```powershell
git add src/app/router.tsx src/pages/landing src/features/shop
git commit -m "feat: build landing page interactions"
```

---

### Task 4: Visual assets and responsive styling

**Files:**
- Create: `public/images/landing/hero-upcycled-denim.webp`
- Create: `public/images/landing/product-denim-circle.webp`
- Create: `public/images/landing/product-work-jacket.webp`
- Create: `public/images/landing/product-saddle-bag.webp`
- Create: `public/images/landing/product-bucket-hat.webp`
- Create: `public/images/landing/product-market-tote.webp`
- Create: `public/images/landing/brand-recode.webp`
- Create: `public/images/landing/brand-dusty-miller.webp`
- Create: `public/images/landing/brand-reweave.webp`
- Create: `public/images/landing/brand-ocean-loop.webp`
- Create: `src/pages/landing/landing.css`
- Modify: `src/index.css`
- Modify: landing component files from Task 3 to import and use the stylesheet/assets

**Interfaces:**
- Consumes: exact slots measured from `screens/landing_page.png`
- Produces: optimized local WebP assets and responsive class names used by Task 3 components

- [ ] **Step 1: Measure and catalog the reference slots**

Record the desktop measurements from the 2560-pixel source and translate them to a 1280-pixel comparison viewport: 32-pixel header height scale, 1216-pixel content maximum, 32-pixel outer gutters, 12-pixel card gaps, and section boundaries matching the source vertical rhythm. Confirm hero, brand-card, product-card, trust-strip, and footer crops before producing assets.

- [ ] **Step 2: Produce individual raster assets**

Use the image generation workflow with the reference image attached. Generate the hero and each product asset independently using the measured aspect ratio, focal point, warm natural light, sustainable/upcycled fashion subject, and muted beige/denim palette. Generate four restrained brand marks on warm ivory fields. Save final files under `public/images/landing/` with the exact names above; inspect every file before wiring it into the page.

- [ ] **Step 3: Replace the starter global CSS**

Keep box sizing and form font inheritance in `src/index.css`, then establish:

```css
:root {
  font-family: "Pretendard", "Noto Sans KR", system-ui, sans-serif;
  color: #172033;
  background: #fbfaf6;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}

html { scroll-behavior: smooth; }
body { min-width: 320px; min-height: 100vh; margin: 0; }
button, input, select { font: inherit; }
button, a { -webkit-tap-highlight-color: transparent; }
```

Remove the old centered `main` and `.wallet` demo-page rules. Move wallet presentation into landing-specific classes without changing wallet logic.

- [ ] **Step 4: Implement the desktop reference layout**

In `landing.css`, define reusable colors `--ink: #172033`, `--blue: #2375aa`, `--beige: #e9dcc0`, `--paper: #fbfaf6`, `--soft-blue: #edf3f7`, and `--orange: #ff9868`. Use a 1216-pixel max-width content container at 1280 pixels, a four-column brand grid, and a five-column product grid. Match reference border radii, typography weights, gutters, and section padding before adding embellishment.

- [ ] **Step 5: Add responsive breakpoints and interaction states**

At `max-width: 960px`, wrap brand cards to two columns and product cards to a two-column grid. At `max-width: 640px`, use one-column editorial sections, keep product cards two-up only when 320-pixel card widths remain readable, collapse footer columns, and show the mobile menu. Add visible `:focus-visible`, hover, pressed, menu-open, wallet-open, validation, and subscription-success states.

- [ ] **Step 6: Run automated verification after styling**

Run:

```powershell
npm test
npm run lint
npm run build
```

Expected: all commands exit 0 and test output contains no React warnings.

- [ ] **Step 7: Commit assets and styling**

```powershell
git add public/images/landing src/index.css src/pages/landing
git commit -m "style: match landing page reference"
```

---

### Task 5: Browser verification and design QA

**Files:**
- Create: `design-qa.md`
- Modify: any landing source or asset files needed to resolve P0, P1, or P2 findings

**Interfaces:**
- Consumes: source image and local browser capture at the same 1280-pixel viewport/state
- Produces: `design-qa.md` containing `final result: passed`

- [ ] **Step 1: Start the local Vite app**

Run: `npm run dev -- --host 0.0.0.0 --port 4173 --strictPort`

Expected: Vite serves the project on port 4173 and remains running.

- [ ] **Step 2: Open the root route in the Codex in-app browser**

Open `http://localhost:4173/` in the in-app browser. Inspect the browser console, confirm there are no errors, and test navigation, mobile menu, wishlist, wallet entry point, and newsletter behavior.

- [ ] **Step 3: Capture matching desktop and mobile states**

Capture the page at 1280 pixels wide for the source comparison and at 390 pixels wide for responsive verification. Keep the desktop page at the same scroll state and interaction state as the reference.

- [ ] **Step 4: Run the blocking design QA comparison**

Open `screens/landing_page.png` and the latest 1280-pixel prototype capture together. Write `design-qa.md` with severity-ranked findings for layout, crop, spacing, typography, color, responsiveness, accessibility, and interactions. Set `final result: blocked` until every P0, P1, and P2 item is fixed.

- [ ] **Step 5: Fix and repeat until passed**

For each P0/P1/P2 mismatch, change only the responsible asset, component, or CSS rule; rerun the relevant tests; recapture; and compare again. Stop iterating on optional P3 polish. Finish with the literal line:

```md
final result: passed
```

- [ ] **Step 6: Run fresh final verification**

Run:

```powershell
npm test
npm run lint
npm run build
```

Expected: all tests pass, ESLint reports no errors, TypeScript/Vite build exits 0, and `design-qa.md` says `final result: passed`.

- [ ] **Step 7: Commit verified QA fixes**

```powershell
git add design-qa.md src public/images/landing
git commit -m "test: verify landing page design"
```
