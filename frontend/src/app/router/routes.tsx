import { Navigate, type RouteObject } from 'react-router-dom'
import { SearchPage } from '../../pages/search/SearchPage.tsx'
import { PublicProductDetailPage } from '../../pages/product/PublicProductDetailPage.tsx'
import { AboutPage } from '../../pages/about/AboutPage.tsx'
import { ShopPage } from '../../pages/shop/ShopPage.tsx'
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
import { ConsumerAddressPage } from '../../pages/mypage/ConsumerAddressPage.tsx'
import { ConsumerAskInquiryPage } from '../../pages/mypage/ConsumerAskInquiryPage.tsx'
import { ConsumerCancellationsPage } from '../../pages/mypage/ConsumerCancellationsPage.tsx'
import { ConsumerInquiriesPage } from '../../pages/mypage/ConsumerInquiriesPage.tsx'
import { ConsumerMyPage } from '../../pages/mypage/ConsumerMyPage.tsx'
import { ConsumerOrderDetailPage } from '../../pages/mypage/ConsumerOrderDetailPage.tsx'
import { ConsumerOrdersPage } from '../../pages/mypage/ConsumerOrdersPage.tsx'
import { ConsumerProfilePage } from '../../pages/mypage/ConsumerProfilePage.tsx'
import { ConsumerRecentlyViewedPage } from '../../pages/mypage/ConsumerRecentlyViewedPage.tsx'
import { ConsumerReviewsPage } from '../../pages/mypage/ConsumerReviewsPage.tsx'
import { ConsumerWishlistPage } from '../../pages/mypage/ConsumerWishlistPage.tsx'
import { ProductOrderReturnPage } from '../../pages/mypage/ProductOrderReturnPage.tsx'
import { BrandProcessingPage } from '../../pages/processing/BrandProcessingPage.tsx'
import { BrandProcessingRequestDetailPage } from '../../pages/processing/BrandProcessingRequestDetailPage.tsx'
import { BrandProcessingRequestListPage } from '../../pages/processing/BrandProcessingRequestListPage.tsx'
import { ProcessingPage } from '../../pages/processing/ProcessingPage.tsx'
import { ProcessorProfilePage } from '../../pages/processing/ProcessorProfilePage.tsx'
import { ProcessingPaymentCallbackPage } from '../../pages/processing/ProcessingPaymentCallbackPage.tsx'
import { ProcessingJobDetailPage } from '../../pages/processing/ProcessingJobDetailPage.tsx'
import { BrandProcessingJobListPage } from '../../pages/processing/BrandProcessingJobListPage.tsx'
import { BrandOrderPage } from '../../pages/product/BrandOrderPage.tsx'
import { BrandProductPage } from '../../pages/product/BrandProductPage.tsx'
import { ProductDetailPage } from '../../pages/product/ProductDetailPage.tsx'
import { ProductEditPage } from '../../pages/product/ProductEditPage.tsx'
import { ProductListPage } from '../../pages/product/ProductListPage.tsx'
import { ProductQrPage } from '../../pages/product/ProductQrPage.tsx'
import { ProductRegisterPage } from '../../pages/product/ProductRegisterPage.tsx'
import { ProductionBatchDetailPage } from '../../pages/production/ProductionBatchDetailPage.tsx'
import { ProductionPage } from '../../pages/production/ProductionPage.tsx'
import { PassportPage } from '../../pages/traceability/PassportPage.tsx'
import { PaymentReturnPage } from '../../pages/trade/PaymentReturnPage.tsx'
import { SupplierTradePage } from '../../pages/trade/SupplierTradePage.tsx'
import { TradeDetailPage } from '../../pages/trade/TradeDetailPage.tsx'
import { TradeListPage } from '../../pages/trade/TradeListPage.tsx'
import { BrowseLayout } from './guards/BrowseLayout.tsx'
import { ProtectedRoute } from './guards/ProtectedRoute.tsx'
import { FORBIDDEN_PATH, RoleGuard } from './guards/RoleGuard.tsx'

const landingPageElement = <LandingPage />

const aboutPageElement = <AboutPage />

const shopPageElement = <ShopPage />

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
  { path: '/about', element: aboutPageElement },
  { path: '/shop', element: shopPageElement },
  { path: '/search', element: <SearchPage /> },
  { path: '/shop/:category', element: shopPageElement },
  { path: '/shop/:category/:subcategory', element: shopPageElement },
  { path: '/products/:productId', element: <PublicProductDetailPage /> },
  { path: '/products', element: <Navigate to="/business/products" replace /> },
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

  // ── CONSUMER ─────────────────────────────────────────
  //
  // **사업자 셸 밖이다.** 소비자 계정 화면은 로고·쇼핑 메뉴·푸터를 스스로 갖고 있어
  // (`ConsumerAccountLayout`), `ProtectedRoute` 의 사이드바 셸 안에 넣으면 화면 안에 화면이
  // 또 있는 모양이 된다. 로그인 여부는 `RoleGuard` 가 함께 본다 — 미인증은 /login,
  // 역할이 다르면 /403 으로 갈린다.
  {
    element: <RoleGuard allow={['CONSUMER']} />,
    children: [
      { path: '/mypage', element: <ConsumerMyPage /> },
      // 주문 내역. 홈에 주문을 통째로 쌓아 두면 주문이 늘어날수록 배송지처럼 짧은 정보가
      // 목록 아래로 밀려 내려간다.
      { path: '/mypage/orders', element: <ConsumerOrdersPage /> },
      // 주문 상세보기. 목록 카드의 "주문 상세보기" 에서 들어온다 — 별도 API 없이 목록과
      // 같은 Query 캐시(useConsumerMyPage)에서 주문 번호로 찾아 쓴다.
      { path: '/mypage/orders/:orderId', element: <ConsumerOrderDetailPage /> },
      // 배송지 관리. 마이페이지의 기본 배송지 카드에서 "편집" 으로 들어간다.
      { path: '/mypage/addresses', element: <ConsumerAddressPage /> },
      // 아래 8개는 2026-09-16 사이드바 개편에서 "준비 중"으로 남겨뒀던 화면이다.
      // 프론트엔드 UI만 먼저 채웠다 — 백엔드가 없어 전부 목데이터로 동작한다
      // (각 페이지 컴포넌트 상단 주석 참고).
      { path: '/mypage/cancellations', element: <ConsumerCancellationsPage /> },
      { path: '/mypage/wishlist', element: <ConsumerWishlistPage /> },
      { path: '/mypage/recently-viewed', element: <ConsumerRecentlyViewedPage /> },
      { path: '/mypage/inquiries/new', element: <ConsumerAskInquiryPage /> },
      { path: '/mypage/inquiries', element: <ConsumerInquiriesPage /> },
      { path: '/mypage/reviews', element: <ConsumerReviewsPage /> },
      { path: '/mypage/profile', element: <ConsumerProfilePage /> },
      // 카카오페이 결제창이 돌아오는 자리. 승인·취소·실패가 모두 이 주소로 오고
      // pg_token 유무로 갈린다.
      //
      // ⚠️ 경로가 서버 설정(app.payment.kakao.product-order-return-url)과 같아야 한다.
      // 어긋나면 결제를 마친 사용자가 없는 페이지에 떨어지고, 묶인 개체도 풀리지 않는다.
      { path: '/orders/:orderId/payment/return', element: <ProductOrderReturnPage /> },
    ],
  },

  // ── 로그인 필요 ───────────────────────────────────────
  {
    element: <ProtectedRoute />,
    children: [
      // 보유 소재는 사업자만 본다. 소비자는 소재를 보유하지 않고, ADMIN 은 도메인 기능에
      // 허용이 없다(권한 Matrix §4) — 둘에게는 언제나 빈 목록이 보이던 메뉴였다.
      {
        element: <RoleGuard allow={['SUPPLIER', 'BRAND', 'PROCESSOR']} />,
        children: [{ path: '/inventory', element: <InventoryPage /> }],
      },

      // 기존 업무용 제품은 로그인 뒤에만 본다. 조회 API 와 대표 이미지 모두 인증을 요구하므로
      // 공개 라우트로 두면 비회원에게 401 로 빈 화면이 나간다.
      //
      // 소비자가 QR 로 들어오는 입구는 이 화면이 아니라 `/qr/:qrToken` 다 —
      // 그쪽은 비회원 공개를 유지해야 한다.
      { path: '/business/products', element: <ProductListPage /> },
      { path: '/business/products/:productId', element: <ProductDetailPage /> },

      // 거래 **상세**는 로그인만 요구한다. 판매자와 구매자가 같은 주소를 쓰고 서로 다른
      // 것을 하므로(발송 / 결제·수령) 역할로 좁힐 수 없다 — 당사자인지는 서버가
      // seller_id·buyer_id 로 판단한다. 목록(/trades)은 아래 BRAND 구역에 있다.
      { path: '/trades/:tradeId', element: <TradeDetailPage /> },
      // 카카오페이 결제창이 돌아오는 자리. 승인·취소·실패가 모두 이 주소로 오고
      // pg_token 유무로 갈린다 (PaymentReturnPage 주석 참고).
      { path: '/trades/:tradeId/payment/return', element: <PaymentReturnPage /> },

      {
        element: <RoleGuard allow={['BRAND', 'PROCESSOR']} />,
        children: [{ path: '/processing/jobs/:jobId', element: <ProcessingJobDetailPage /> }],
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
          { path: '/brand/processing/jobs', element: <BrandProcessingJobListPage /> },
          { path: '/processing/jobs/:jobId/payment/callback', element: <ProcessingPaymentCallbackPage /> },
          { path: '/brand/production', element: <ProductionPage /> },
          // 배치 상세에서 소재를 투입하고 확정한다. 목록에서 눌러 들어가는 화면이라
          // 사이드바에는 두지 않는다.
          { path: '/brand/production/:batchId', element: <ProductionBatchDetailPage /> },
          // 제품 등록. 서버도 BRAND 만 허용하고 역할을 DB 에서 다시 읽어 판단한다.
          { path: '/brand/products/new', element: <ProductRegisterPage /> },
          // 내 제품 관리(판매 시작·중단). 업무 목록(/business/products)과 달리 DRAFT 를 포함한다.
          { path: '/brand/products', element: <BrandProductPage /> },
          // 제품 수정. 목록에서 눌러 들어가는 화면이라 사이드바에는 두지 않는다.
          { path: '/brand/products/:productModelId/edit', element: <ProductEditPage /> },
          // 들어온 주문의 발송 처리. 제품 관리와 나눈다 — 그쪽은 "무엇을 파는가" 이고
          // 여기는 "무엇을 오늘 보내야 하는가" 라 주기가 다르다.
          { path: '/brand/orders', element: <BrandOrderPage /> },

          // 소재 구매 내역. **BRAND 만 구매를 끝낼 수 있다** — 서버의 수령 확인
          // (`POST /v1/trades/{id}/receive/confirm`)이 `hasRole('BRAND')` 다. 그래서
          // SUPPLIER·PROCESSOR 에게 이 목록은 언제나 비어 있고, 사이드바에 "거래 내역"
          // 이 보이는데 눌러도 아무것도 없는 화면이 됐다.
          //
          // 판매자는 /supplier/trades 로 자기가 판 거래(side=SELL)를 본다. 상세는 위의
          // 로그인 구역에 있어 양쪽이 모두 들어간다 — 목록만 관점이 갈린다.
          { path: '/trades', element: <TradeListPage /> },
          // 그 제품의 QR 라벨 미리보기. 개체의 qrToken 은 공개 경로의 열쇠라 소유자
          // 전용이어야 한다 — 로그인한 누구나 보는 /business/products/:id 에는 두지 않는다.
          { path: '/brand/products/:productModelId/qr', element: <ProductQrPage /> },
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
