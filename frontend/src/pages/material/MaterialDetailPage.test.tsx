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
  // 증빙 블록이 EvidenceUpload 로 위임되면서 이 페이지가 네 번째 엔드포인트를 쓴다.
  getEvidence: vi.fn(),
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
vi.mock('../../features/evidence/api/getEvidence.ts', () => ({
  getEvidence: api.getEvidence,
}))

function detail(overrides: Partial<MaterialDetail> = {}): MaterialDetail {
  return {
    // 서버는 상세에서 materialId 로 준다. id 는 목록 쪽 이름이라 둘 다 채워 둔다 —
    // 화면이 잘못된 쪽을 읽으면 여기서 드러나야 한다.
    id: 12,
    materialId: 12,
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
    originReason: 'DEADSTOCK',
    originReasonNote: null,
    composition: [],
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
  // `GET /api/evidence` 는 배열이 아니라 객체를 준다 (S15P21E104-286 §6-1).
  // 증빙 0건은 `summary` 가 빈 배열인 것으로 표현된다 — 이 페이지 테스트는 증빙
  // 내용을 보지 않으므로 가장 단순한 "0건" 응답을 쓴다.
  api.getEvidence.mockReset().mockResolvedValue({ visibility: 'FULL', files: [], summary: [] })
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

  it('tokenId 가 null 이면 미등록으로 보여주고 Token ID 를 그리지 않는다', async () => {
    // 0 이나 빈칸으로 그리면 발행된 것처럼 읽힌다. 발행 버튼은 등록자 본인에게만
    // 보이므로(MaterialMintPanel 이 판정한다) 여기서는 배지만 확인한다.
    api.getMaterial.mockResolvedValue(detail({ tokenId: null }))
    renderPage()

    expect(await screen.findByText('미등록')).toBeInTheDocument()
    expect(screen.queryByText('Token ID')).not.toBeInTheDocument()
  })

  it('tokenId 가 있으면 온체인 정보를 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({ tokenId: 77, chainId: 1337 }))
    renderPage()

    expect(await screen.findByText('77')).toBeInTheDocument()
    expect(screen.getByText('1337')).toBeInTheDocument()
    expect(screen.queryByText('미등록')).not.toBeInTheDocument()
    expect(screen.getByText('온체인 #77')).toBeInTheDocument()
  })

  it('증빙은 EvidenceUpload 에 위임하고 소재 id 로 조회한다', async () => {
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    await waitFor(() => expect(api.getEvidence).toHaveBeenCalledWith('MATERIAL', 12))
    expect(await screen.findByText('등록된 증빙이 없습니다')).toBeInTheDocument()
  })

  it('material.evidenceHash 를 증빙 해시로 보여주지 않는다', async () => {
    // 그 컬럼은 **manifest 해시**(여러 증빙을 묶은 JSON 의 해시) 자리이고 개별 파일
    // 해시가 아니다. 개별 해시는 evidence_file 이 갖는다. 둘을 섞으면 "증빙 없음" 인데
    // 파일은 있는 상태(또는 그 반대)가 화면에 그려진다.
    const manifestHash = '0x' + 'cd'.repeat(32)
    api.getMaterial.mockResolvedValue(detail({ evidenceHash: manifestHash }))
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.queryByText(manifestHash)).not.toBeInTheDocument()
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

describe('구성 · 발생 사유', () => {
  it('구성이 없으면 EmptyState 다 — 오류가 아니다', async () => {
    api.getMaterial.mockResolvedValue(detail({ composition: [] }))
    renderPage()

    expect(await screen.findByText('구성 정보가 없습니다')).toBeInTheDocument()
  })

  it('서버가 준 순서대로 그린다', async () => {
    // 서버가 비율 내림차순으로 정렬해 준다. 여기서 다시 정렬하면 같은 소재의 줄 순서가
    // 화면마다 달라 보인다.
    api.getMaterial.mockResolvedValue(detail({
      composition: [
        { fiberType: 'COTTON', percentage: 60 },
        { fiberType: 'POLYESTER', percentage: 40 },
      ],
    }))
    renderPage()

    expect(await screen.findByText('면')).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument()
    expect(screen.getByText('폴리에스터')).toBeInTheDocument()
    expect(screen.getByText('40%')).toBeInTheDocument()
  })

  it('합계 100 이면 미확인 문구를 띄우지 않는다', async () => {
    api.getMaterial.mockResolvedValue(detail({
      composition: [
        { fiberType: 'COTTON', percentage: 60 },
        { fiberType: 'POLYESTER', percentage: 40 },
      ],
    }))
    renderPage()

    await screen.findByText('면')
    expect(screen.queryByText(/확인되지 않았습니다/)).not.toBeInTheDocument()
  })

  it('합계가 100 미만이면 나머지가 미확인임을 밝힌다', async () => {
    // 밝히지 않으면 데이터 누락으로 읽힌다. 실제로는 지어내지 않은 결과다.
    api.getMaterial.mockResolvedValue(detail({
      composition: [{ fiberType: 'COTTON', percentage: 60 }],
    }))
    renderPage()

    expect(await screen.findByText(/나머지 40% 는 확인되지 않았습니다/)).toBeInTheDocument()
  })

  it('라벨 없는 섬유 이름은 원본을 그대로 보여준다', async () => {
    // 컬럼이 VARCHAR(50) 이라 허용 목록 밖의 값이 이미 저장돼 있을 수 있다.
    // 빈칸으로 두면 성분이 하나 사라진 것처럼 보인다.
    api.getMaterial.mockResolvedValue(detail({
      composition: [{ fiberType: 'HEMP', percentage: 100 }],
    }))
    renderPage()

    expect(await screen.findByText('HEMP')).toBeInTheDocument()
  })

  it('폐기물이면 발생 사유를 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({
      rootOrigin: 'WASTE',
      originReason: 'OFFCUT',
    }))
    renderPage()

    expect(await screen.findByText('재단 자투리')).toBeInTheDocument()
  })

  it('설명이 있으면 사유와 함께 보여준다', async () => {
    api.getMaterial.mockResolvedValue(detail({
      rootOrigin: 'WASTE',
      originReason: 'ETC',
      originReasonNote: '행사 후 남은 배너 원단',
    }))
    renderPage()

    expect(await screen.findByText(/기타 · 행사 후 남은 배너 원단/)).toBeInTheDocument()
  })

  it('신소재면 발생 사유 줄을 그리지 않는다', async () => {
    // 신소재에는 "왜 폐기물이 되었나" 라는 개념이 없다. "미지정" 으로 두면 입력을
    // 빠뜨린 것처럼 보인다.
    api.getMaterial.mockResolvedValue(detail({
      rootOrigin: 'VIRGIN',
      originReason: null,
    }))
    renderPage()

    await screen.findByText('MAT-000012')
    expect(screen.queryByText('발생 사유')).not.toBeInTheDocument()
  })
})

describe('블록 조합', () => {
  it('티켓이 요구한 블록을 모두 그린다', async () => {
    api.getMaterial.mockResolvedValue(detail())
    renderPage()

    await screen.findByText('MAT-000012')
    for (const name of [
      '소재 정보',
      '구성',
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
