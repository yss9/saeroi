import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * <b>사업자 셸은 게이트에서 한 번만 씌운다.</b>
 *
 * 메뉴는 원래 화면마다 넣는 구조였고, 그래서 실제로 두 곳에만 붙어 있었다. 나머지 보호
 * 화면에는 메뉴가 없어 한 번 대시보드를 벗어나면 돌아갈 길이 없었다. 그래서
 * {@code ProtectedRoute} 가 {@code <Outlet />} 을 {@code CompanyDashboardLayout} 으로
 * 감싸도록 옮겼다(16f3a83).
 *
 * 그 뒤로 화면이 자기도 다시 감싸면 <b>셸이 두 벌 그려진다</b> — 헤더도 사이드바도 메뉴도
 * 둘이다. 실제로 렌더해 확인했다: 중첩하면 "판매 관리" 링크가 2개가 된다.
 *
 * 화면 파일을 읽어 검사하는 이유는, 이 사고가 <b>새로 만든 화면</b>에서 나기 때문이다.
 * 옛 화면을 복사해 시작하면 래퍼가 딸려 오고, 렌더 테스트는 그 화면을 따로 짜야만
 * 잡는다. 여기서는 파일만 보면 되므로 화면이 늘어도 저절로 지켜진다.
 */
// fileURLToPath 를 쓴다. URL.pathname 은 Windows 에서 `/C:/...` 를 돌려줘 경로가 깨진다.
const PAGES_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'pages')

/**
 * 아직 정리되지 않은 화면들. <b>목록이 늘어나면 안 된다.</b>
 *
 * 셸을 게이트로 옮길 당시에는 자기 래핑하는 화면이 하나도 없었는데, 그 전에 갈라진
 * 브랜치들이 옛 패턴 그대로 들어오면서 다시 생겼다. 남의 도메인 화면이라 이 MR 에서
 * 함께 고치지 않고 목록으로 남긴다 — 고치는 것은 각 담당자의 몫이고, 그때 이 목록에서
 * 지우면 된다.
 *
 * 새 화면이 여기 없이 래핑하면 이 테스트가 막는다.
 */
const KNOWN_UNFIXED = [
  'processing/BrandProcessingPage.tsx',
  'processing/BrandProcessingRequestDetailPage.tsx',
  'processing/BrandProcessingRequestListPage.tsx',
  'processing/ProcessingPage.tsx',
  'processing/ProcessingPaymentCallbackPage.tsx',
  'production/ProductionPage.tsx',
]

function pageFiles(dir: string, prefix = ''): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name
    if (entry.isDirectory()) return pageFiles(join(dir, entry.name), rel)
    if (!entry.name.endsWith('.tsx') || entry.name.includes('.test.')) return []
    return [rel]
  })
}

describe('사업자 셸 중첩', () => {
  it('화면이 스스로 CompanyDashboardLayout 을 씌우지 않는다', () => {
    const offenders = pageFiles(PAGES_DIR).filter((rel) =>
      readFileSync(join(PAGES_DIR, rel), 'utf-8').includes('<CompanyDashboardLayout'),
    )

    expect(offenders.sort()).toEqual([...KNOWN_UNFIXED].sort())
  })
})
