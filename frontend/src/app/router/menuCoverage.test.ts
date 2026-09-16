import { describe, expect, it } from 'vitest'
import { ALL_MENU_ITEMS } from '../navigation/navigationMenu.ts'
import { getRouteAccessMap } from './routeAccess.ts'

/**
 * <b>화면을 만들었는데 메뉴에 넣지 않으면 아무도 못 찾는다.</b>
 *
 * 실제로 그렇게 됐다 — {@code /marketplace} 는 브랜드가 소재를 사는 입구인데 사이드바
 * 어디에도 링크가 없어서, 주소를 직접 치지 않으면 갈 수 없었다.
 * {@code /materials} · {@code /products} 도 같았다.
 *
 * 라우터에 화면을 더하는 사람과 메뉴를 관리하는 사람이 같지 않으니, 사람이 기억하는
 * 대신 여기서 대조한다. 새 화면을 넣으면 이 테스트가 "메뉴에 넣든지, 안 넣는 이유를
 * 적든지" 를 요구한다.
 */

/**
 * 메뉴에 없는 것이 <b>정상</b>인 경로.
 *
 * 각 줄에 이유를 적는다 — 이유 없이 늘어나면 이 테스트가 무력해진다.
 */
const NOT_IN_MENU: ReadonlyMap<string, string> = new Map([
  ['/', '랜딩. 로그인 전 입구이고 헤더 로고가 데려간다'],
  ['/about', '공개 브랜드 소개. 쇼핑몰 헤더·OUR STORY·푸터에서 접근하므로 사업자 사이드바에서 제외'],
  ['/shop', '공개 소비자 상품 목록. 쇼핑몰 헤더·VIEW ALL PRODUCTS에서 접근하므로 사업자 사이드바에서 제외'],
  ['/login', '인증 흐름'],
  ['/signup', '인증 흐름'],
  ['/signup/consumer', '인증 흐름'],
  ['/signup/business', '인증 흐름'],
  ['/signup/complete', '인증 흐름. 가입 직후에만 지나간다'],
  ['/403', '가드가 보내는 안내 화면'],
  ['/admin', '관리자 전용. ACCOUNT_MENU_ITEMS 가 따로 들고 있다'],
  ['/mypage', '소비자 홈. ACCOUNT_MENU_ITEMS 가 따로 들고 있다'],
  ['/products', '소비자가 옷을 사는 화면. 쇼핑 머리말의 "쇼핑" 으로 들어간다 — 사업자 사이드바에 둘 자리가 아니다'],
  ['/mypage/orders', '주문 내역. 마이페이지 사이드바에서 들어간다'],
  ['/mypage/addresses', '배송지 관리. 마이페이지의 기본 배송지 카드에서 들어간다'],
])

/** 상세·콜백처럼 목록에서 눌러 들어가는 경로는 메뉴에 둘 대상이 아니다. */
function isDrillDown(path: string): boolean {
  return path.includes(':')
}

describe('사이드바 메뉴 커버리지', () => {
  it('라우터에 있는 화면은 메뉴에 있거나, 없는 이유가 적혀 있다', () => {
    const linked = new Set(ALL_MENU_ITEMS.map((item) => item.to))

    const orphans = [...getRouteAccessMap().keys()]
      .filter((path) => !isDrillDown(path))
      .filter((path) => !linked.has(path))
      .filter((path) => !NOT_IN_MENU.has(path))

    expect(orphans).toEqual([])
  })

  it('메뉴가 가리키는 경로는 전부 라우터에 있다', () => {
    // 반대 방향. 라우트를 지우거나 이름을 바꾸면 메뉴가 404 로 데려간다.
    const broken = ALL_MENU_ITEMS.map((item) => item.to).filter(
      (to) => getRouteAccessMap().get(to) === undefined,
    )

    expect(broken).toEqual([])
  })

  it('메뉴에서 빼 둔 이유 목록에 죽은 항목이 없다', () => {
    // 라우트가 사라졌는데 면제만 남으면, 나중에 같은 경로가 다른 뜻으로 생겼을 때
    // 조용히 면제된다.
    const stale = [...NOT_IN_MENU.keys()].filter((path) => getRouteAccessMap().get(path) === undefined)

    expect(stale).toEqual([])
  })
})
