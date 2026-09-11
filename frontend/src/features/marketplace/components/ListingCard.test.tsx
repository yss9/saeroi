import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { Listing } from '../types/marketplace.types.ts'
import { ListingCard } from './ListingCard.tsx'

const listing: Listing = {
  listingId: 7,
  chainListingId: null,
  materialId: 3,
  materialCode: 'MAT-000003',
  materialName: '폐데님 원단',
  materialDescription: null,
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  generationType: 'INITIAL',
  grade: null,
  sellerId: 2,
  sellerName: '공급사',
  totalQuantity: 10_000,
  availableQuantity: 8_000,
  pricePerKg: 5_000,
  status: 'ON_SALE',
  createdAt: '2026-09-11T01:00:00',
}

afterEach(cleanup)

function LocationProbe() {
  return <output aria-label="현재 경로">{useLocation().pathname}</output>
}

it('판매글 카드의 빈 영역을 클릭하면 상세로 이동한다', async () => {
  const user = userEvent.setup()
  render(
    <MemoryRouter initialEntries={['/marketplace']}>
      <ListingCard listing={listing} />
      <LocationProbe />
    </MemoryRouter>,
  )

  await user.click(screen.getByRole('article'))

  expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/marketplace/7')
})

it('카드 내부 동작 버튼을 클릭하면 상세로 이동하지 않는다', async () => {
  const user = userEvent.setup()
  const action = vi.fn()
  render(
    <MemoryRouter initialEntries={['/supplier/listings']}>
      <ListingCard listing={listing} actions={<button type="button" onClick={action}>판매 취소</button>} />
      <LocationProbe />
    </MemoryRouter>,
  )

  await user.click(screen.getByRole('button', { name: '판매 취소' }))

  expect(action).toHaveBeenCalledOnce()
  expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/supplier/listings')
})
