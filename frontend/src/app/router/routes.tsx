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
import { MaterialRegisterPage } from '../../pages/material/MaterialRegisterPage.tsx'
import { ConsumerMyPage } from '../../pages/mypage/ConsumerMyPage.tsx'
import { BrandProcessingPage } from '../../pages/processing/BrandProcessingPage.tsx'
import { BrandProcessingRequestDetailPage } from '../../pages/processing/BrandProcessingRequestDetailPage.tsx'
import { BrandProcessingRequestListPage } from '../../pages/processing/BrandProcessingRequestListPage.tsx'
import { ProcessingPage } from '../../pages/processing/ProcessingPage.tsx'
import { ProcessorProfilePage } from '../../pages/processing/ProcessorProfilePage.tsx'
import { ProcessingPaymentCallbackPage } from '../../pages/processing/ProcessingPaymentCallbackPage.tsx'
import { BrandProductPage } from '../../pages/product/BrandProductPage.tsx'
import { ProductDetailPage } from '../../pages/product/ProductDetailPage.tsx'
import { ProductListPage } from '../../pages/product/ProductListPage.tsx'
import { ProductRegisterPage } from '../../pages/product/ProductRegisterPage.tsx'
import { ProductionPage } from '../../pages/production/ProductionPage.tsx'
import { PassportPage } from '../../pages/traceability/PassportPage.tsx'
import { PaymentReturnPage } from '../../pages/trade/PaymentReturnPage.tsx'
import { SupplierTradePage } from '../../pages/trade/SupplierTradePage.tsx'
import { TradeDetailPage } from '../../pages/trade/TradeDetailPage.tsx'
import { TradeListPage } from '../../pages/trade/TradeListPage.tsx'
import { BrowseLayout } from './guards/BrowseLayout.tsx'
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
  // BrowseLayout 으로 감싸는 이유: RoleGuard 가 로그인한 사용자를 여기로 보내는데,
  // 셸이 없으면 그 순간 사이드바를 잃는다. 유일한 출구가 "홈으로 돌아가기"(랜딩)라
  // 대시보드로 돌아오려면 한 번 더 헤매야 했다. 비회원에게는 그대로 안내 화면만 보인다.
  {
    element: <BrowseLayout />,
    children: [{ path: FORBIDDEN_PATH, element: <ForbiddenPage /> }],
  },

  // QR Passport: 비회원 공개. 절대 ProtectedRoute 안으로 옮기지 말 것.
  //
  // `:productId` 가 아니라 `:qrToken` 이다 (S15P21E104-136). productId 는 순번이라
  // URL 을 하나씩 바꿔가며 남의 제품 이력을 볼 수 있다 — qrToken 이 추측 불가능한
  // 값이라 이 경로의 유일한 접근 통제다 (§6-3, 2026-09-08).
  { path: '/qr/:qrToken', element: <PassportPage /> },

  // ── 로그인 필요 ───────────────────────────────────────
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/inventory', element: <InventoryPage /> },

      // 제품은 로그인 뒤에만 본다. 조회 API 와 대표 이미지 모두 인증을 요구하므로
      // 공개 라우트로 두면 비회원에게 401 로 빈 화면이 나간다.
      //
      // 소비자가 QR 로 들어오는 입구는 이 화면이 아니라 `/qr/:qrToken` 다 —
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
      //
      // 소재 거래 구역은 <b>사업자만</b>이다. 예전에는 공개였는데 두 가지가 어긋나 있었다.
      //
      // 첫째, 비회원에게 화면은 뜨고 데이터는 401 이었다 — SecurityConfig 가 열어 둔 것은
      // `/api/v1/auth/**` 와 헬스체크, QR 뿐이라 `GET /v1/listings` 가 그대로 막힌다.
      // 필터바만 있고 목록이 비어 있는 화면이 나갔다. 둘째, 업사이클링 제품을 사는
      // CONSUMER 에게도 메뉴가 보였다 — 소재는 소비자가 사는 물건이 아니다.
      //
      // <b>SUPPLIER 를 빼지 않는다.</b> 마켓플레이스에 있는 판매 글이 곧 공급업체가 올린
      // 것이라, 막으면 자기가 올린 글이 어떻게 보이는지조차 확인할 수 없다. 구매만
      // BRAND 로 좁혀져 있고(TradeCommandService — 온체인 `createOrder` 가 BRAND_ROLE 을
      // 요구한다), 그 제약은 서버와 컨트랙트가 이미 두 겹으로 막는다.
      //
      // ADMIN 도 넣지 않는다 — 권한 Matrix §4 에서 도메인 기능에 허용이 없고 지갑이 없어
      // 서명 흐름에 들어갈 수 없다. 다른 RoleGuard 들과 같은 판단이다.
      {
        element: <RoleGuard allow={['SUPPLIER', 'BRAND', 'PROCESSOR']} />,
        children: [
          { path: '/company/account', element: <CompanyAccountPage /> },
          { path: '/materials/:materialId', element: <MaterialDetailPage /> },
          { path: '/marketplace', element: <MarketplacePage /> },
          { path: '/marketplace/:listingId', element: <ListingDetailPage /> },
        ],
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
          { path: '/brand/processing/requests', element: <BrandProcessingRequestListPage /> },
          { path: '/brand/processing/requests/:requestId', element: <BrandProcessingRequestDetailPage /> },
          { path: '/processing/jobs/:jobId/payment/callback', element: <ProcessingPaymentCallbackPage /> },
          { path: '/brand/production', element: <ProductionPage /> },
          // 제품 등록. 서버도 BRAND 만 허용하고 역할을 DB 에서 다시 읽어 판단한다.
          { path: '/brand/products/new', element: <ProductRegisterPage /> },
          // 내 제품 관리(판매 시작·중단). 마켓 목록(/products)과 달리 DRAFT 를 포함한다.
          { path: '/brand/products', element: <BrandProductPage /> },
        ],
      },

      // ── PROCESSOR ──
      {
        element: <RoleGuard allow={['PROCESSOR']} />,
        children: [
          { path: '/processor/jobs', element: <ProcessingPage /> },
          { path: '/processor/profile', element: <ProcessorProfilePage /> },
        ],
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
