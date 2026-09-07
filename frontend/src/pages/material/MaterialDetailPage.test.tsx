// 소재 상세 페이지.
//
// Page 는 조합만 하므로 여기서 지키는 건 "무엇을 보여주고 무엇을 보여주지 않는가" 다.
// 특히 서버가 아직 사실을 모르는 값을 사실처럼 보여주지 않는 쪽을 고정한다 —
// 추적성 화면에서 근거 없는 확정 표시는 빈칸보다 나쁘다.
//
// API 모듈 세 개를 모킹한다. 블록마다 다른 엔드포인트를 쓰는 것이 이 화면의 설계라서,
// 그 경계를 그대로 테스트 경계로 삼는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  MaterialBalance,
  MaterialDetail,
  MaterialLineage,
} from '../../features/material/types/material.types.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { MaterialDetailPage } from './MaterialDetailPage.tsx'

const api = vi.hoisted(() => ({
  getMaterial: vi.fn(),
  getMaterialBalances: vi.fn(),
  getMaterialLineage: vi.fn(),
}))
vi.mock('../../features/material/api/getMaterial.ts', () => ({
  getMaterial: api.getMaterial,
}))
vi.mock('../../features/material/api/getMaterialBalances.ts', () => ({
  getMaterialBalances: api.getMaterialBalances,
}))
vi.mock('../../features/material/api/getMaterialLineage.ts', () => ({
  getMaterialLineage: api.getMaterialLineage,
}))

function detail(overrides: Partial<MaterialDetail> = {}): MaterialDetail {
  return {
    id: 12,
    materialCode: 'MAT-000012',
    materialType: 'DENIM',
    name: '수거 데님 원단',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    verificationState: 'REGISTERED',
    status: 'ACTIVE',
    totalQuantity: 20_500,
    grade: 'B',
    description: '중고 의류에서 수거한 데님',
    lengthMm: 1_400,
    widthMm: 900,
    registrantId: 3,
    createdAt: '2026-09-01T10:30:00',
    owners: [],
    evidenceHash: null,
    manifestUrl: null,
    tokenId: null,
    chainId: null,
    contractAddress: null,
    ...overrides,
  }
}

const BALANCE: MaterialBalance = {
  ownerId: 3,
  walletAddress: '0x3cdA1c35B5F84c71C3Bc2A6c1A3C3aFC5D1C5F04',
  quantity: 20_500,
  lockedQuantity: 5_000,
  available: 15_500,
  syncedAt: '2026-09-07T09:00:00',
}

const EMPTY_LINEAGE: MaterialLineage = {
  materialId: 12,
  materialCode: 'MAT-000012',
  nodes: [],
  edges: [],
  truncated: false,
  message: null,
}

function renderPage(path = '/materials/12') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/materials/:materialId" element={<MaterialDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMaterial.mockReset()
  api.getMaterialBalances.mockReset().mockResolvedValue([BALANCE])
  api.getMaterialLineage.mockReset().mockResolvedValue(EMPTY_LINEAGE)
})

afterEach(cleanup)

describe('소재 정보', () => {
  it('이름·코드·기본 정보를 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: '수거 데님 원단' }),
    ).toBeInTheDocument()
    expect(screen.getByText('MAT-000012')).toBeInTheDocument()
    expect(screen.getByText('폐기물')).toBeInTheDocument()
    expect(screen.getByText('B · 경미한 오염·손상')).toBeInTheDocument()
  })

  it('총 수량을 kg 단위와 함께 보여준다', async () => {
    // gram 을 단위 없이 그리면 20,500 이 20.5kg 인지 20.5t 인지 알 수 없다.
    api.getMaterial.mockResolvedValue(detail({ totalQuantity: 20_500 }))
    renderPage()

    expect(await screen.findByText('20.5 kg')).toBeInTheDocument()
  })

  it('등급이 없으면 미지정으로 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({ grade: null }))
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.getByText('미지정')).toBeInTheDocument()
  })

  it('규격이 하나만 있으면 그것만 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({ widthMm: null }))
    renderPage()

    expect(await screen.findByText('1,400 mm')).toBeInTheDocument()
  })

  it('규격이 둘 다 없으면 곱셈 기호를 그리지 않는다', async () => {
    api.getMaterial.mockResolvedValue(detail({ lengthMm: null, widthMm: null }))
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.queryByText(/×/)).not.toBeInTheDocument()
  })
})

describe('근거 없는 값을 확정으로 보여주지 않는다', () => {
  it('verificationState 로 검증 배지를 그리지 않는다', async () => {
    // 서버는 항상 REGISTERED 를 준다 — 컬럼도 판정 근거(signed_at)도 스키마에 없다.
    // 배지를 그리면 실제로 검증된 소재도 "등록만 됨" 으로 보인다.
    api.getMaterial.mockResolvedValue(detail({ verificationState: 'REGISTERED' }))
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.queryByText('등록됨')).not.toBeInTheDocument()
    expect(screen.getByText('검증 단계 표시 준비 중')).toBeInTheDocument()
  })

  it('tokenId 가 null 이면 온체인 미확정으로 보여준다', async () => {
    // confirm 엔드포인트가 없어 지금은 모든 소재가 이 상태다. 0 이나 빈칸으로
    // 그리면 발행된 것처럼 읽힌다.
    api.getMaterial.mockResolvedValue(detail({ tokenId: null }))
    renderPage()

    expect(await screen.findByText('온체인 미확정')).toBeInTheDocument()
    expect(screen.queryByText('Token ID')).not.toBeInTheDocument()
  })

  it('tokenId 가 있으면 온체인 정보를 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({ tokenId: 77, chainId: 1337 }))
    renderPage()

    expect(await screen.findByText('77')).toBeInTheDocument()
    expect(screen.getByText('1337')).toBeInTheDocument()
    expect(screen.queryByText('온체인 미확정')).not.toBeInTheDocument()
  })

  it('증빙이 없으면 준비 중임을 밝힌다', async () => {
    api.getMaterial.mockResolvedValue(detail({ evidenceHash: null }))
    renderPage()

    expect(await screen.findByText('등록된 증빙이 없습니다')).toBeInTheDocument()
  })

  it('증빙 해시가 있으면 그대로 보여준다', async () => {
    const hash = '0x' + 'ab'.repeat(32)
    api.getMaterial.mockResolvedValue(detail({ evidenceHash: hash }))
    renderPage()

    expect(await screen.findByText(hash)).toBeInTheDocument()
  })

  it('아직 없는 블록은 자리만 두고 이유를 밝힌다', async () => {
    // 티켓이 요구한 7블록 중 판매 정보는 조회 API 가 없다. 빈 자리를 말없이 두면
    // 사용자는 고장으로 읽는다.
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.getByRole('heading', { name: '판매 정보' })).toBeInTheDocument()
    expect(screen.getByText('판매 정보 준비 중')).toBeInTheDocument()
  })
})

describe('블록 조합', () => {
  it('티켓이 요구한 블록을 모두 그린다', async () => {
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    await screen.findByText('MAT-000012')
    for (const name of [
      '소재 정보',
      '온체인',
      '보유 현황',
      '소재 이력',
      '증빙',
      '검증 상태',
      '판매 정보',
    ]) {
      expect(screen.getByRole('heading', { name })).toBeInTheDocument()
    }
  })

  it('보유 현황과 이력은 각자 조회한다', async () => {
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    await waitFor(() => {
      expect(api.getMaterialBalances).toHaveBeenCalledWith(12)
      expect(api.getMaterialLineage).toHaveBeenCalledWith(12)
    })
  })

  it('한 블록이 실패해도 나머지는 살아 있다', async () => {
    // 페이지에서 모아 부르면 하나가 실패했을 때 화면 전체가 에러가 된다.
    api.getMaterial.mockResolvedValue(detail())
    api.getMaterialBalances.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '보유 현황을 불러오지 못했습니다.' }),
    )
    renderPage()

    expect(await screen.findByText('보유 현황을 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: '수거 데님 원단' })).toBeInTheDocument()
    expect(screen.getByText('폐기물')).toBeInTheDocument()
  })
})

describe('페이지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMaterial.mockReturnValue(new Promise(() => {}))
    renderPage()

    expect(screen.getByRole('status', { name: '소재 정보를 불러오는 중' })).toBeInTheDocument()
  })

  it('상세 조회가 실패하면 에러를 보여준다', async () => {
    api.getMaterial.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '소재를 찾을 수 없습니다.' }),
    )
    renderPage()

    expect(await screen.findByText('소재를 찾을 수 없습니다.')).toBeInTheDocument()
  })

  it.each(['/materials/abc', '/materials/0', '/materials/1.5', '/materials/-3'])(
    '%s 는 조회하지 않고 잘못된 주소로 처리한다',
    async (path) => {
      // 사용자가 URL 을 직접 고칠 수 있다. 그대로 넘기면 서버에 의미 없는 요청이 간다.
      renderPage(path)

      expect(await screen.findByText('잘못된 소재 주소입니다.')).toBeInTheDocument()
      expect(api.getMaterial).not.toHaveBeenCalled()
    },
  )
})
