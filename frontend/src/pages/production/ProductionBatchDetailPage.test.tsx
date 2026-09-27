// 생산 화면에서 배치를 연 뒤 멈추면 이유를 들고 이 화면으로 온다. 그 이유를 한 번 보여준다.

import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProductionBatchDetailPage } from './ProductionBatchDetailPage.tsx'

vi.mock('../../features/production/components/ProductionBatchDetail.tsx', () => ({
  ProductionBatchDetail: ({ batchId }: { batchId: number }) => <p>배치 {batchId}</p>,
}))

function renderAt(state: unknown) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/brand/production/3', state }]}>
      <Routes>
        <Route path="/brand/production/:batchId" element={<ProductionBatchDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProductionBatchDetailPage', () => {
  afterEach(cleanup)

  it('생산 화면이 넘긴 멈춘 이유를 보여준다', () => {
    renderAt({ productionFailure: '서명을 취소했어요.' })

    expect(screen.getByText(/생산을 끝까지 기록하지 못했어요. 서명을 취소했어요./)).toBeTruthy()
    expect(screen.getByText('배치 3')).toBeTruthy()
  })

  it('끝까지 마치고 왔으면 경고가 없다', () => {
    renderAt({ productionFailure: null })

    expect(screen.queryByText(/끝까지 기록하지 못했어요/)).toBeNull()
  })
})
