// 메뉴 항목 정의.
//
// **여기에는 역할 조건이 없다.** 각 항목은 목적지 경로만 들고 있고, 누가 보는지는
// `routeAccess` 가 routes.tsx 의 ProtectedRoute / RoleGuard 중첩에서 파생한다.
// 즉 "SUPPLIER 는 소재 판매 메뉴를 본다" 라는 정책을 이 파일이 정하지 않는다 —
// routes.tsx 의 `<RoleGuard allow={['SUPPLIER']} />` 가 정하고 여기는 그 결과를 따른다.
// 새 매핑을 발명하지 않기 위한 구조다 (routeAccess.ts 머리말 참고).
//
// ⚠️ 메뉴를 숨기는 것은 보안이 아니다. 숨겨도 사용자가 URL 을 직접 치면 그 경로로
// 이동한다 — 거기서 막는 것은 RoleGuard(UX)와 서버 인가(실제 경계)다.
// 그래서 메뉴에서 빠뜨려도 데이터가 새지 않고, 반대로 메뉴에 있다고 해서 권한이
// 생기지도 않는다.

import {
  Buildings,
  ClipboardText,
  Factory,
  Package,
  ShieldCheck,
  ShoppingCart,
  Storefront,
  Swap,
  Tag,
  Truck,
  UserCircle,
} from '@phosphor-icons/react'

export interface NavigationMenuItem {
  readonly label: string
  /** routes.tsx 에 정의된 경로여야 한다. 없으면 테스트가 실패한다 */
  readonly to: string
  readonly Icon: typeof Buildings
}

/**
 * 사업자 대시보드 사이드바 메뉴 (CompanyDashboardLayout).
 *
 * 순서는 역할별로 나누지 않고 하나로 둔다. 역할별 배열을 만들면 그 자체가
 * 역할→메뉴 매핑이 되어(= 두 번째 정의) routes.tsx 와 어긋날 수 있다.
 * 아래 순서는 각 역할이 걸러진 뒤에도 자연스럽게 읽히도록 골랐다.
 *
 *   SUPPLIER  회사 정보 · 보유 소재 · 소재 등록 · 판매 관리 · 판매 거래 · 거래 내역
 *   BRAND     회사 정보 · 보유 소재 · 가공 신청 · 요청 내역 · 생산 관리 · 제품 등록 · 내 제품 · 거래 내역
 *   PROCESSOR 회사 정보 · 보유 소재 · 업체 등록 · 가공 작업 · 거래 내역
 *
 * `/inventory`, `/trades` 는 RoleGuard 가 없는 Protected 경로다 = 로그인만 하면
 * 역할 무관. 예전 사이드바는 이 둘에 역할 조건을 직접 붙여(SUPPLIER 는 거래 내역만,
 * PROCESSOR 는 둘 다 없음) 라우터에 없는 규칙을 화면에만 만들어 두고 있었다.
 * 권한 Matrix(§3-3·§3-5)도 그 경로들을 역할로 좁히지 않았으므로,
 * 새 조건을 붙이는 대신 라우터 정의에 맞춘다.
 */
export const BUSINESS_MENU_ITEMS: readonly NavigationMenuItem[] = [
  { label: '회사 정보', to: '/company/account', Icon: Buildings },
  { label: '보유 소재', to: '/inventory', Icon: Package },

  // 조회 화면. 라우터에는 있는데 메뉴에 없어서 주소를 직접 치지 않으면 갈 수 없었다.
  // **마켓플레이스가 빠져 있던 것이 특히 문제였다** — 브랜드가 소재를 사는 입구인데
  // 대시보드 어디에도 링크가 없었다.
  { label: '소재 마켓플레이스', to: '/marketplace', Icon: ShoppingCart },

  { label: '소재 등록', to: '/supplier/materials/new', Icon: ClipboardText },
  { label: '판매 관리', to: '/supplier/listings', Icon: Storefront },
  { label: '판매 거래', to: '/supplier/trades', Icon: Truck },
  { label: '가공 신청', to: '/brand/processing', Icon: Factory },
  { label: '요청 내역', to: '/brand/processing/requests', Icon: ClipboardText },
  { label: '가공 진행', to: '/brand/processing/jobs', Icon: Factory },
  { label: '생산 관리', to: '/brand/production', Icon: Buildings },
  { label: '제품 등록', to: '/brand/products/new', Icon: Tag },
  { label: '내 제품', to: '/brand/products', Icon: Storefront },
  { label: '업체 등록', to: '/processor/profile', Icon: Buildings },
  { label: '가공 작업', to: '/processor/jobs', Icon: Factory },
  { label: '제품 둘러보기', to: '/products', Icon: Tag },
  { label: '거래 내역', to: '/trades', Icon: Swap },
]

/**
 * 헤더 계정 팝오버가 쓰는 "내 홈" 후보. 보이는 것 중 **첫 번째**를 쓴다.
 *
 * 역할별 주요 업무 경로를 앞에 두고 계정 홈을 뒤에 둔다. 사업자는 회사 정보에도
 * 접근할 수 있지만, 헤더는 필터를 통과한 첫 항목을 골라 각 역할의 주요 업무 화면으로
 * 바로 연결한다.
 */
export const ROLE_HOME_MENU_ITEMS: readonly NavigationMenuItem[] = [
  { label: '판매 관리', to: '/supplier/listings', Icon: Storefront },
  { label: '가공 요청', to: '/brand/processing', Icon: Factory },
  { label: '견적 요청', to: '/processor/jobs', Icon: ClipboardText },
  { label: '마이페이지', to: '/mypage', Icon: UserCircle },
  { label: '관리자 페이지', to: '/admin', Icon: ShieldCheck },
  { label: '회사 대시보드', to: '/company/account', Icon: Buildings },
]

/** 사업자 셸 안에서 소비자·관리자에게 필요한 계정 메뉴 후보. */
export const ACCOUNT_MENU_ITEMS: readonly NavigationMenuItem[] = [
  { label: '마이페이지', to: '/mypage', Icon: UserCircle },
  { label: '관리자 페이지', to: '/admin', Icon: ShieldCheck },
]

/** 메뉴 경로가 routes.tsx 에 실제로 있는지 검사하는 테스트용 전체 목록. */
export const ALL_MENU_ITEMS: readonly NavigationMenuItem[] = [
  ...BUSINESS_MENU_ITEMS,
  ...ACCOUNT_MENU_ITEMS,
  ...ROLE_HOME_MENU_ITEMS,
]
