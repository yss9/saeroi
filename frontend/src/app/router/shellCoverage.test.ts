import { isValidElement, type ReactElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { BrowseLayout } from './guards/BrowseLayout.tsx'
import { ProtectedRoute } from './guards/ProtectedRoute.tsx'
import { routes } from './routes.tsx'

/**
 * <b>로그인한 사용자는 어느 화면에서도 사이드바를 잃지 않아야 한다.</b>
 *
 * 셸을 씌우는 것은 {@code ProtectedRoute}(로그인 필수 구역)와
 * {@code BrowseLayout}(공개 구역이지만 로그인했으면 씌운다) 둘뿐이다. 그 밖에 있는
 * 화면에 로그인한 사용자가 들어가면 메뉴가 사라지고, 돌아올 길은 브라우저 뒤로 가기나
 * 마케팅 랜딩을 경유하는 것뿐이다.
 *
 * 실제로 그렇게 됐던 곳이 셋이다 — 마켓플레이스·소재 목록(둘 다 공개라 셸 밖)과
 * {@code /403}(RoleGuard 가 로그인한 사용자를 보내는 곳). 라우트를 더하는 사람이
 * 셸을 매번 기억하는 대신 여기서 대조한다.
 */

/**
 * 셸이 <b>없는 것이 정상</b>인 경로. 각 줄에 이유를 적는다.
 *
 * 이유 없이 늘어나면 이 테스트가 무력해지므로, 새 경로를 넣기 전에 정말 셸 밖에
 * 있어야 하는지 먼저 따진다.
 */
const INTENTIONALLY_BARE: ReadonlyMap<string, string> = new Map([
  ['/brands', '공개 브랜드 목록. 자체 쇼핑몰 헤더·푸터 사용'],
  ['/brands/:brandId', '공개 브랜드 상세. 자체 쇼핑몰 헤더·푸터 사용'],
  ['/search', '공개 소비자 검색. 자체 쇼핑몰 헤더·푸터 사용'],
  ['/products', '기존 목록 호환 리다이렉트. 목적지에서 인증 및 셸 적용'],
  ['/products/:productId', '공개 소비자 상세. 자체 쇼핑몰 헤더·푸터 사용'],
  ['/', '쇼핑몰 랜딩. 자체 헤더를 갖고 있고 로그인 상태면 역할 홈 링크를 보여준다'],
  ['/about', '공개 브랜드 소개. 쇼핑몰 공통 헤더·푸터와 계정 메뉴를 사용하므로 사업자 셸에서 제외'],
  ['/shop', '소비자 상품 목록. 쇼핑몰 공통 헤더·푸터와 계정 메뉴 사용'],
  ['/shop/:category', '소비자 상품 상위 분류. SHOP과 같은 레이아웃 사용'],
  ['/shop/:category/:subcategory', '소비자 상품 하위 분류. SHOP과 같은 레이아웃 사용'],
  ['/login', '인증 흐름. AuthLayout 이 따로 있다'],
  ['/signup', '인증 흐름'],
  ['/signup/consumer', '인증 흐름'],
  ['/signup/business', '인증 흐름'],
  ['/signup/complete', '가입 직후. 아직 로그인 상태가 아니다'],
  ['/qr/:qrToken', 'QR 로 들어오는 비회원 입구. 사업자 껍데기가 어울리지 않는다'],
  ['/mypage', '소비자 계정 화면. ConsumerAccountLayout 이 자체 머리말·푸터를 갖는다'],
  ['/mypage/orders', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/orders/:orderId', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/addresses', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/cancellations', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/wishlist', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/recently-viewed', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/inquiries/new', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/inquiries', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/reviews', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/profile', '소비자 계정 화면. 마이페이지와 같은 껍데기를 쓴다'],
  ['/mypage/predictions', '소비자 예측 화면. ConsumerAccountLayout을 쓴다'],
  ['/orders/:orderId/payment/return', '카카오페이에서 돌아오는 자리. 소비자 구역이라 사업자 셸이 어울리지 않는다'],
])

interface Walked {
  readonly path: string
  readonly hasShell: boolean
}

function isElementOf(element: unknown, component: unknown): element is ReactElement {
  return isValidElement(element) && element.type === component
}

function walk(list: readonly RouteObject[], hasShell: boolean, out: Walked[]): void {
  for (const route of list) {
    const shell =
      hasShell ||
      isElementOf(route.element, ProtectedRoute) ||
      isElementOf(route.element, BrowseLayout)

    if (route.path !== undefined && route.path !== '*') {
      out.push({ path: route.path, hasShell: shell })
    }
    if (route.children) walk(route.children, shell, out)
  }
}

function walkRoutes(): Walked[] {
  const out: Walked[] = []
  walk(routes, false, out)
  return out
}

describe('사이드바 셸 커버리지', () => {
  it('셸 밖에 있는 화면은 그 이유가 적혀 있다', () => {
    const bare = walkRoutes()
      .filter((entry) => !entry.hasShell)
      .map((entry) => entry.path)
      .filter((path) => !INTENTIONALLY_BARE.has(path))

    expect(bare).toEqual([])
  })

  it('일부러 셸을 뺀 목록에 죽은 경로가 없다', () => {
    const known = new Set(walkRoutes().map((entry) => entry.path))
    const stale = [...INTENTIONALLY_BARE.keys()].filter((path) => !known.has(path))

    expect(stale).toEqual([])
  })

  it('거래·결제처럼 흐름 중간에 지나가는 화면은 모두 셸 안에 있다', () => {
    // 결제 복귀(`/trades/:tradeId/payment/return`)에서 셸이 빠지면, 카카오페이에서
    // 돌아온 사용자가 메뉴 없는 화면에 떨어진다. 흐름의 마지막 단계라 특히 나쁘다.
    const byPath = new Map(walkRoutes().map((entry) => [entry.path, entry.hasShell]))

    for (const path of [
      '/inventory',
      '/marketplace',
      '/marketplace/:listingId',
      '/materials/:materialId',
      '/supplier/listings',
      '/supplier/materials/new',
      '/trades',
      '/trades/:tradeId',
      '/trades/:tradeId/payment/return',
    ]) {
      expect(byPath.get(path), `${path} 에 셸이 없다`).toBe(true)
    }
  })
})
