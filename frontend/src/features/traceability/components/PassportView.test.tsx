// QR Passport 공개 화면.
//
// 이 화면에서 틀리면 안 되는 지점은 둘이다.
//
//   1) **없는 이력을 확인된 것처럼 그리지 않는다.** traceabilityAvailable 이 false 인
//      동안 소재 출처·거래·가공 단계는 "완료"가 아니라 "준비 중"이어야 한다 — 위조
//      여부를 가리는 이 화면의 존재 이유가 무너지면 안 된다.
//   2) **이미지 주소를 저장 키로 만들지 않는다.** Passport 응답에는 애초에
//      thumbnailUrl 이 없다 — 전용 공개 엔드포인트를 qrToken 으로 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ProductPassport } from '../types/traceability.types.ts'
import { PassportView } from './PassportView.tsx'

const api = vi.hoisted(() => ({ getPassport: vi.fn() }))
vi.mock('../api/getPassport.ts', () => ({
  getPassport: api.getPassport,
  getPassportThumbnailUrl: (qrToken: string) => `/api/v1/qr/${qrToken}/thumbnail`,
}))

function passport(overrides: Partial<ProductPassport> = {}): ProductPassport {
  return {
    itemCode: 'ITEM-0001',
    serialNumber: 1,
    itemStatus: 'SOLD',
    issuedAt: '2026-09-08T10:00:00',
    product: {
      name: 'RE:BAG',
      category: 'BAG',
      description: '폐데님으로 만든 가방입니다.',
    },
    traceabilityAvailable: false,
    ...overrides,
  }
}

function renderView(qrToken = 'a'.repeat(32)) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <PassportView qrToken={qrToken} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getPassport.mockResolvedValue(passport())
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('PassportView', () => {
  it('제품 정보를 보여준다', async () => {
    renderView()

    expect(await screen.findByRole('heading', { name: 'RE:BAG' })).toBeInTheDocument()
    expect(screen.getByText('가방')).toBeInTheDocument()
    expect(screen.getByText('ITEM-0001')).toBeInTheDocument()
    expect(screen.getByText(/폐데님으로 만든 가방/)).toBeInTheDocument()
  })

  it('qrToken 으로 전용 썸네일 엔드포인트를 직접 부른다', async () => {
    renderView('deadbeef00000000000000000000000')

    const image = await screen.findByRole('img', { name: /대표 이미지/ })
    expect(image.getAttribute('src')).toBe(
      '/api/v1/qr/deadbeef00000000000000000000000/thumbnail',
    )
  })

  it('소재 출처를 아직 잇지 못했으면 확인된 것처럼 보이지 않는다', async () => {
    renderView()

    expect(await screen.findByText('제품 확인 완료')).toBeInTheDocument()
    expect(screen.getByText('소재 출처 확인 준비 중')).toBeInTheDocument()
    expect(screen.getByText('거래 확인 준비 중')).toBeInTheDocument()
    expect(screen.getByText('가공 과정 확인 준비 중')).toBeInTheDocument()
    expect(screen.queryByText('소재 출처 확인 완료')).not.toBeInTheDocument()
  })

  it('소재 출처가 연결되면 완료로 보여준다', async () => {
    api.getPassport.mockResolvedValue(passport({ traceabilityAvailable: true }))
    renderView()

    expect(await screen.findByText('소재 출처 확인 완료')).toBeInTheDocument()
    expect(screen.getByText('거래 확인 완료')).toBeInTheDocument()
    expect(screen.getByText('가공 과정 확인 완료')).toBeInTheDocument()
  })

  it('개체 상태를 블록체인 용어 없이 말한다', async () => {
    api.getPassport.mockResolvedValue(passport({ itemStatus: 'SOLD' }))
    renderView()

    expect(await screen.findByText('제품 사용 확인 완료')).toBeInTheDocument()
  })

  it('아직 팔리지 않은 개체는 그렇게 말한다', async () => {
    api.getPassport.mockResolvedValue(passport({ itemStatus: 'IN_STOCK' }))
    renderView()

    expect(await screen.findByText('아직 판매되지 않은 제품입니다.')).toBeInTheDocument()
  })

  it('모르는 토큰이면 다시 시도할 수 있게 한다', async () => {
    api.getPassport.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'QR_TOKEN_NOT_FOUND',
        message: '일치하는 제품을 찾을 수 없습니다.',
        status: 404,
      }),
    )
    renderView()

    expect(await screen.findByRole('alert')).toHaveTextContent('일치하는 제품을 찾을 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
