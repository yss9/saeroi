import { type RouteObject } from 'react-router-dom'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { LandingPage } from '../../pages/landing/LandingPage.tsx'
import { AdminPage } from '../../pages/auth/AdminPage.tsx'
import { BusinessSignupPage } from '../../pages/auth/BusinessSignupPage.tsx'
import { ConsumerSignupPage } from '../../pages/auth/ConsumerSignupPage.tsx'
import { ForbiddenPage } from '../../pages/auth/ForbiddenPage.tsx'
import { LoginPage } from '../../pages/auth/LoginPage.tsx'
import { NotFoundPage } from '../../pages/auth/NotFoundPage.tsx'
import { SignupCompletePage } from '../../pages/auth/SignupCompletePage.tsx'
import { SignupTypePage } from '../../pages/auth/SignupTypePage.tsx'
import { CompanyAccountPage } from '../../pages/company/CompanyAccountPage.tsx'
import { InventoryPage } from '../../pages/inventory/InventoryPage.tsx'
import { ListingDetailPage } from '../../pages/marketplace/ListingDetailPage.tsx'
import { MarketplacePage } from '../../pages/marketplace/MarketplacePage.tsx'
import { SupplierMarketPage } from '../../pages/marketplace/SupplierMarketPage.tsx'
import { MaterialDetailPage } from '../../pages/material/MaterialDetailPage.tsx'
import { MaterialListPage } from '../../pages/material/MaterialListPage.tsx'
import { MaterialRegisterPage } from '../../pages/material/MaterialRegisterPage.tsx'
import { ConsumerMyPage } from '../../pages/mypage/ConsumerMyPage.tsx'
import { BrandProcessingPage } from '../../pages/processing/BrandProcessingPage.tsx'
import { ProcessingPage } from '../../pages/processing/ProcessingPage.tsx'
import { ProductDetailPage } from '../../pages/product/ProductDetailPage.tsx'
import { ProductListPage } from '../../pages/product/ProductListPage.tsx'
import { ProductRegisterPage } from '../../pages/product/ProductRegisterPage.tsx'
import { ProductionPage } from '../../pages/production/ProductionPage.tsx'
import { PassportPage } from '../../pages/traceability/PassportPage.tsx'
import { PaymentReturnPage } from '../../pages/trade/PaymentReturnPage.tsx'
import { SupplierTradePage } from '../../pages/trade/SupplierTradePage.tsx'
import { TradeDetailPage } from '../../pages/trade/TradeDetailPage.tsx'
import { TradeListPage } from '../../pages/trade/TradeListPage.tsx'
import { ProtectedRoute } from './guards/ProtectedRoute.tsx'
import { FORBIDDEN_PATH, RoleGuard } from './guards/RoleGuard.tsx'

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
  { path: '/signup', element: <SignupTypePage /> },
  { path: '/signup/consumer', element: <ConsumerSignupPage /> },
  { path: '/signup/business', element: <BusinessSignupPage /> },
  { path: '/signup/complete', element: <SignupCompletePage /> },

  // 권한 없음(403 성격) 안내. RoleGuard 가 여기로 보낸다.
  // Public 에 두는 이유: 이 화면 자체가 가드에 막히면 사용자가 갈 곳이 없어진다.
  { path: FORBIDDEN_PATH, element: <ForbiddenPage /> },

  // QR Passport: 비회원 공개. 절대 ProtectedRoute 안으로 옮기지 말 것.
  { path: '/passport/:productId', element: <PassportPage /> },

  // 소재 목록은 공개 정보다. 등록·거래처럼 쓰기 동작만 인증을 요구한다.
  //
  // ⚠️ 다만 이 구역의 화면들이 부르는 API 는 대부분 인증을 요구한다 — SecurityConfig 가
  // `/api/v1/auth/**` 와 헬스체크만 열어 두었다. 비회원에게 무엇을 보여줄지는 QR 공개
  // 조회와 함께 정해야 하는 별도 결정이라 여기서 우회하지 않는다.
  { path: '/materials', element: <MaterialListPage /> },
  { path: '/materials/:materialId', element: <MaterialDetailPage /> },
  { path: '/marketplace', element: <MarketplacePage /> },
  { path: '/marketplace/:listingId', element: <ListingDetailPage /> },

  // ── 로그인 필요 ───────────────────────────────────────
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/inventory', element: <InventoryPage /> },

      // 제품은 로그인 뒤에만 본다. 조회 API 와 대표 이미지 모두 인증을 요구하므로
      // 공개 라우트로 두면 비회원에게 401 로 빈 화면이 나간다.
      //
      // 소비자가 QR 로 들어오는 입구는 이 화면이 아니라 `/passport/:productId` 다 —
      // 그쪽은 비회원 공개를 유지해야 한다.
      { path: '/products', element: <ProductListPage /> },
      { path: '/products/:productId', element: <ProductDetailPage /> },

      { path: '/trades', element: <TradeListPage /> },
      { path: '/trades/:tradeId', element: <TradeDetailPage /> },
      // 카카오페이 결제창이 돌아오는 자리. 승인·취소·실패가 모두 이 주소로 오고
      // pg_token 유무로 갈린다 (PaymentReturnPage 주석 참고).
      { path: '/trades/:tradeId/payment/return', element: <PaymentReturnPage /> },

      // ── CONSUMER ──
      {
        element: <RoleGuard allow={['CONSUMER']} />,
        children: [{ path: '/mypage', element: <ConsumerMyPage /> }],
      },

      // ── BUSINESS 공통 ──
      {
        element: <RoleGuard allow={['SUPPLIER', 'BRAND', 'PROCESSOR']} />,
        children: [{ path: '/company/account', element: <CompanyAccountPage /> }],
      },

      // ── SUPPLIER ──
      {
        element: <RoleGuard allow={['SUPPLIER']} />,
        children: [
          { path: '/supplier/materials/new', element: <MaterialRegisterPage /> },
          // 정책 07.1: Supplier 뿐 아니라 소재를 보유한 Brand 도 재판매할 수 있다.
          // 지금은 SUPPLIER 구역에 두고, Brand 재판매 진입점은 별도로 논의한다.
          { path: '/supplier/listings', element: <SupplierMarketPage /> },
          // 판매 거래 목록. 발송 처리 자체는 /trades/:tradeId 에서 한다 — 발송은
          // 역할이 아니라 그 거래의 판매자인지로 갈리므로 상세가 당사자를 보고 판단한다.
          { path: '/supplier/trades', element: <SupplierTradePage /> },
        ],
      },

      // ── BRAND ──
      {
        element: <RoleGuard allow={['BRAND']} />,
        children: [
          { path: '/brand/processing', element: <BrandProcessingPage /> },
          { path: '/brand/production', element: <ProductionPage /> },
          // 제품 등록. 서버도 BRAND 만 허용하고 역할을 DB 에서 다시 읽어 판단한다.
          { path: '/brand/products/new', element: <ProductRegisterPage /> },
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
