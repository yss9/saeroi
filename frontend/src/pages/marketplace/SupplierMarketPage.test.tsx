// 소재 상세("보유 소재 팔기")에서 ?materialId= 로 들어왔을 때 그 소재가 미리 선택돼야
// 한다. 이걸 놓치면 사용자가 방금 누른 소재를 판매 등록 화면에서 또 찾아 골라야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { InventoryItem } from '../../features/inventory/types/inventory.types.ts'
import { SupplierMarketPage } from './SupplierMarketPage.tsx'

const api = vi.hoisted(() => ({
  getMyMaterials: vi.fn(),
  getListings: vi.fn(),
  getMaterial: vi.fn(),
}))

vi.mock('../../features/inventory/api/getMyMaterials.ts', () => ({
  getMyMaterials: api.getMyMaterials,
}))
vi.mock('../../features/marketplace/api/getListings.ts', () => ({
  getListings: api.getListings,
}))
vi.mock('../../features/material/api/getMaterial.ts', () => ({
  getMaterial: api.getMaterial,
}))

const DENIM: InventoryItem = {
  materialId: 1,
  materialCode: 'MAT-000001',
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  generationType: 'INITIAL',
  name: '세척된 폐데님',
  grade: 'A',
  status: 'ACTIVE',
  totalQuantity: 30_000,
  quantity: 30_000,
  lockedQuantity: 0,
  available: 30_000,
  syncedAt: '2026-09-07T00:00:00.000Z',
}

const COTTON: InventoryItem = { ...DENIM, materialId: 2, materialCode: 'MAT-000002', name: '재생 면 원사' }

function renderPage(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <SupplierMarketPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMyMaterials.mockReset()
  api.getListings.mockReset()
  api.getMaterial.mockReset()
  api.getListings.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
  api.getMaterial.mockResolvedValue({ tokenId: null })
  useAuthStore.setState({
    user: { id: 9, email: 'brand@test.com', name: '공급자', role: 'SUPPLIER', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
})

afterEach(cleanup)

describe('소재 상세에서 넘어온 판매 등록', () => {
  it('materialId 쿼리와 일치하는 소재를 미리 선택해 폼을 바로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM, COTTON])
    renderPage('/supplier/listings?materialId=2')

    expect(await screen.findByText('재생 면 원사')).toBeInTheDocument()
    expect(screen.queryByText('판매할 소재를 먼저 선택하세요')).not.toBeInTheDocument()
  })

  it('보유하지 않은 소재 id 면 평소처럼 선택을 비워 둔다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    renderPage('/supplier/listings?materialId=999')

    await waitFor(() => expect(api.getMyMaterials).toHaveBeenCalled())
    expect(await screen.findByText('판매할 소재를 먼저 선택하세요')).toBeInTheDocument()
  })

  it('쿼리가 없으면 기존처럼 선택을 비워 둔다', async () => {
    api.getMyMaterials.mockResolvedValue([DENIM])
    renderPage('/supplier/listings')

    expect(await screen.findByText('판매할 소재를 먼저 선택하세요')).toBeInTheDocument()
  })
})
