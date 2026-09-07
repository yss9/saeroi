import { type RouteObject } from 'react-router-dom'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { LandingPage } from '../../pages/landing/LandingPage.tsx'
import { AdminPage } from '../../pages/auth/AdminPage.tsx'
import { LoginPage } from '../../pages/auth/LoginPage.tsx'
import { NotFoundPage } from '../../pages/auth/NotFoundPage.tsx'
import { InventoryPage } from '../../pages/inventory/InventoryPage.tsx'
import { ListingDetailPage } from '../../pages/marketplace/ListingDetailPage.tsx'
import { MarketplacePage } from '../../pages/marketplace/MarketplacePage.tsx'
import { SupplierMarketPage } from '../../pages/marketplace/SupplierMarketPage.tsx'
import { MaterialDetailPage } from '../../pages/material/MaterialDetailPage.tsx'
import { MaterialListPage } from '../../pages/material/MaterialListPage.tsx'
import { MaterialRegisterPage } from '../../pages/material/MaterialRegisterPage.tsx'
import { ProcessingPage } from '../../pages/processing/ProcessingPage.tsx'
import { ProductDetailPage } from '../../pages/product/ProductDetailPage.tsx'
import { ProductListPage } from '../../pages/product/ProductListPage.tsx'
import { ProductionPage } from '../../pages/production/ProductionPage.tsx'
import { PassportPage } from '../../pages/traceability/PassportPage.tsx'
import { TradeDetailPage } from '../../pages/trade/TradeDetailPage.tsx'
import { TradeListPage } from '../../pages/trade/TradeListPage.tsx'
import { ProtectedRoute } from './guards/ProtectedRoute.tsx'
import { RoleGuard } from './guards/RoleGuard.tsx'

const landingPageElement = (
  <ShopProvider>
    <LandingPage />
  </ShopProvider>
)

/**
 * 라우트 정의.
 *
 * 세 구역으로 나뉜다.
 *
 *   1) Public       — 로그인·지갑 없이 접근. QR Passport 가 여기 속한다.
 *   2) Protected    — 로그인 필요
 *   3) Role 별 구역 — Protected 안에 RoleGuard 를 중첩
 *
 * 새 라우트는 반드시 이 파일에만 추가한다. 페이지 파일에서 라우트를 정의하면
 * 전체 경로 구조를 한눈에 볼 수 없게 되고 중복 경로가 생긴다.
 */
export const routes: RouteObject[] = [
  // ── Public ────────────────────────────────────────────
  { path: '/', element: landingPageElement },
  { path: '/login', element: <LoginPage /> },

  // QR Passport: 비회원 공개. 절대 ProtectedRoute 안으로 옮기지 말 것.
  { path: '/passport/:productId', element: <PassportPage /> },

  // 소재·제품 목록은 공개 정보다. 등록·거래처럼 쓰기 동작만 인증을 요구한다.
  { path: '/materials', element: <MaterialListPage /> },
  { path: '/materials/:materialId', element: <MaterialDetailPage /> },
  { path: '/marketplace', element: <MarketplacePage /> },
  { path: '/marketplace/:listingId', element: <ListingDetailPage /> },
  { path: '/products', element: <ProductListPage /> },
  { path: '/products/:productId', element: <ProductDetailPage /> },

  // ── 로그인 필요 ───────────────────────────────────────
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/inventory', element: <InventoryPage /> },
      { path: '/trades', element: <TradeListPage /> },
      { path: '/trades/:tradeId', element: <TradeDetailPage /> },

      // ── SUPPLIER ──
      {
        element: <RoleGuard allow={['SUPPLIER']} />,
        children: [
          { path: '/supplier/materials/new', element: <MaterialRegisterPage /> },
          // 정책 07.1: Supplier 뿐 아니라 소재를 보유한 Brand 도 재판매할 수 있다.
          // 지금은 SUPPLIER 구역에 두고, Brand 재판매 진입점은 별도로 논의한다.
          { path: '/supplier/listings', element: <SupplierMarketPage /> },
        ],
      },

      // ── BRAND ──
      {
        element: <RoleGuard allow={['BRAND']} />,
        children: [
          { path: '/brand/processing', element: <ProcessingPage /> },
          { path: '/brand/production', element: <ProductionPage /> },
        ],
      },

      // ── PROCESSOR ──
      {
        element: <RoleGuard allow={['PROCESSOR']} />,
        children: [{ path: '/processor/jobs', element: <ProcessingPage /> }],
      },

      // ── ADMIN ──
      {
        element: <RoleGuard allow={['ADMIN']} />,
        children: [{ path: '/admin', element: <AdminPage /> }],
      },
    ],
  },

  // 정의되지 않은 경로. 예전에는 랜딩으로 보냈지만, 그러면 오타 URL 이 정상 화면처럼
  // 보여 사용자가 잘못을 눈치채지 못한다.
  { path: '*', element: <NotFoundPage /> },
]
