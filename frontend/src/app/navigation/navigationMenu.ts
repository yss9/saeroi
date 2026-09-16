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
  Cube,
  Factory,
  Gear,
  IdentificationCard,
  Package,
  PaperPlaneTilt,
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
 *   SUPPLIER  회사 정보 · 보유 소재 · 소재 등록 · 판매 관리 · 판매 거래
 *   BRAND     회사 정보 · 보유 소재 · 가공 요청 · 요청 내역 · 제품 생산 · 제품 등록 · 내 제품 · 제품 발송 · 구매 내역
 *   PROCESSOR 회사 정보 · 보유 소재 · 업체 등록 · 가공 작업
 *
 * **공개 상품(`/shop`)은 사이드바에 없다.** 소비자가 옷을 사는 화면이라 사업자
 * 메뉴에 둘 일이 아니고, 그 자리에 있으면 브랜드가 자기 제품을 눌러 상세로 들어갔을 때
 * 사이드바의 "제품 둘러보기" 가 켜진다 — 자기가 어디에 있는지 잘못 읽힌다. 소비자는
 * 쇼핑 머리말의 "쇼핑" 으로 들어간다.
 *
 * `/inventory` 는 RoleGuard 가 없는 Protected 경로다 = 로그인만 하면 역할 무관.
 * 예전 사이드바는 여기에 역할 조건을 직접 붙여 라우터에 없는 규칙을 화면에만 만들어
 * 두고 있었다. 그런 조건은 이 파일에 두지 않는다 — 좁혀야 한다면 routes.tsx 의
 * 가드를 고친다.
 *
 * **구매 내역(`/trades`)이 그렇게 좁혀진 예다.** SUPPLIER·PROCESSOR 에게 언제나 비어
 * 있었는데(서버의 수령 확인이 BRAND 전용이라 그 역할은 구매를 끝낼 수 없다) 메뉴에는
 * 보였다. 메뉴에서 지우는 대신 routes.tsx 에서 BRAND 구역으로 옮겼고, 이 파일은 그
 * 결과를 따른다.
 */
export interface NavigationMenuGroup {
  /** 사이드바에 접힌 채로 보이는 이름 */
  readonly label: string
  readonly items: readonly NavigationMenuItem[]
}

/**
 * 사업자 사이드바 메뉴 — **일의 종류로 묶는다.**
 *
 * 평평한 목록은 브랜드에게 11줄이었다. 브랜드가 하는 일은 크게 셋인데(소재를 사고, 가공을
 * 맡기고, 제품을 판다) 그 구조가 화면에 드러나지 않아 매번 목록 전체를 훑어야 했다.
 *
 * **묶음은 역할 조건이 아니다.** 어느 역할이 무엇을 보는지는 여전히 routes.tsx 의
 * RoleGuard 가 정하고, 이 파일은 "같은 일끼리 모은다" 만 말한다 — 비는 묶음은 화면이
 * 그리지 않으므로 역할마다 다른 배열을 둘 필요가 없다.
 *
 *   BRAND     회사 정보 + 소재(3) · 가공(3) · 제품(4)
 *   SUPPLIER  회사 정보 + 소재(5)
 *   PROCESSOR 회사 정보 + 소재(2) · 가공(2)
 */
/**
 * 묶음 밖에 두는 항목.
 *
 * **회사 정보는 묶지 않는다.** 항목이 하나뿐이라 접었다 펴는 동작이 그 자체로 방해이고,
 * 계정 화면으로 돌아가는 길은 언제나 한 번에 닿아야 한다.
 */
export const BUSINESS_TOP_ITEMS: readonly NavigationMenuItem[] = [
  { label: '회사 정보', to: '/company/account', Icon: Buildings },
]

export const BUSINESS_MENU_GROUPS: readonly NavigationMenuGroup[] = [
  {
    label: '소재',
    items: [
      // 브랜드가 소재를 사는 입구다. 이게 메뉴에 없어서 대시보드에서 갈 수가 없었다.
      { label: '소재 검색', to: '/marketplace', Icon: ShoppingCart },
      { label: '구매 내역', to: '/trades', Icon: Swap },
      { label: '보유 소재', to: '/inventory', Icon: Package },
      // 아래 셋은 파는 쪽이다. 사는 일과 파는 일이 같은 묶음인 것은 다루는 대상이
      // 소재로 같기 때문이고, 한 역할에게 둘 다 보이는 경우는 없다.
      { label: '소재 등록', to: '/supplier/materials/new', Icon: ClipboardText },
      { label: '판매 관리', to: '/supplier/listings', Icon: Storefront },
      { label: '판매 거래', to: '/supplier/trades', Icon: Truck },
    ],
  },
  {
    label: '가공',
    items: [
      { label: '가공 요청', to: '/brand/processing', Icon: PaperPlaneTilt },
      { label: '요청 내역', to: '/brand/processing/requests', Icon: ClipboardText },
      { label: '가공 진행', to: '/brand/processing/jobs', Icon: Gear },
      // 가공사 쪽. 맡기는 일과 받는 일이 같은 묶음이다.
      { label: '업체 등록', to: '/processor/profile', Icon: IdentificationCard },
      { label: '가공 작업', to: '/processor/jobs', Icon: Factory },
    ],
  },
  {
    label: '제품',
    items: [
      // 브랜드의 일은 생산에서 시작한다 — 소재를 넣어 만들고, 그 결과가 제품이 된다.
      { label: '제품 생산', to: '/brand/production', Icon: Cube },
      { label: '제품 등록', to: '/brand/products/new', Icon: Tag },
      { label: '내 제품', to: '/brand/products', Icon: Storefront },
      { label: '제품 발송', to: '/brand/orders', Icon: Truck },
    ],
  },
]

/**
 * 묶음을 편 목록.
 *
 * 라우트 대조 테스트와 계정 메뉴 병합이 이 모양을 쓴다 — 묶음 구조는 화면의 사정이고,
 * "어느 경로가 메뉴에 있는가" 는 그것과 무관하다.
 */
export const BUSINESS_MENU_ITEMS: readonly NavigationMenuItem[] = [
  ...BUSINESS_TOP_ITEMS,
  ...BUSINESS_MENU_GROUPS.flatMap((group) => group.items),
]

/**
 * 하위 화면을 가진 항목의 경로.
 *
 * **이 경로들은 정확히 일치할 때만 켜야 한다.** `NavLink` 는 기본이 접두사 비교라,
 * `/brand/products` 가 `/brand/products/new` 에서도 켜진다 — 제품 등록 화면에서 "내 제품" 이
 * 함께 강조되어 지금 어디에 있는지 잘못 읽힌다.
 *
 * 목록을 손으로 적지 않는다. 다른 항목이 자기 아래에 있는지 보면 되고, 그래야 메뉴가
 * 늘어날 때 빠뜨리지 않는다.
 */
export const EXACT_MATCH_PATHS: ReadonlySet<string> = new Set(
  BUSINESS_MENU_ITEMS.filter((item) =>
    BUSINESS_MENU_ITEMS.some((other) => other.to.startsWith(`${item.to}/`)),
  ).map((item) => item.to),
)

/**
 * 헤더 계정 팝오버가 쓰는 "내 홈" 후보. 보이는 것 중 **첫 번째**를 쓴다.
 *
 * **계정 화면을 앞에 둔다.** 사람 모양 버튼이 가리키는 곳은 "내 계정" 이지 업무 화면이
 * 아니다 — 예전에는 역할별 업무 경로가 앞에 있어 브랜드가 그 버튼을 누르면 가공 요청으로
 * 갔다. 업무 화면은 사이드바가 이미 전부 들고 있다.
 */
export const ROLE_HOME_MENU_ITEMS: readonly NavigationMenuItem[] = [
  { label: '마이페이지', to: '/mypage', Icon: UserCircle },
  { label: '관리자 페이지', to: '/admin', Icon: ShieldCheck },
  // 사업자의 계정 화면이다. 소비자의 마이페이지와 같은 자리라 이름도 맞춘다.
  { label: '마이페이지', to: '/company/account', Icon: Buildings },
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
