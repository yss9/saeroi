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

// 가로형 카드는 세 덩어리다 — 왼쪽 이미지 자리, 가운데 소재, 오른쪽 값. 여러 장을 훑을 때
// 단가가 언제나 같은 자리에 와야 서로 견줄 수 있다.
it('소재와 값을 한 줄에 나눠 보여준다', () => {
  render(
    <MemoryRouter>
      <ListingCard listing={listing} />
    </MemoryRouter>,
  )

  expect(screen.getByRole('link', { name: '폐데님 원단' })).toBeInTheDocument()
  // 소재 코드는 적지 않는다 — 사람이 읽는 값이 아니고 상세에 있다.
  expect(screen.queryByText('MAT-000003')).not.toBeInTheDocument()
  expect(screen.getByText('판매 중')).toBeInTheDocument()
  expect(screen.getByText('데님')).toBeInTheDocument()
  expect(screen.getByText(/5,000원/)).toBeInTheDocument()
  expect(screen.getByText('8 kg')).toBeInTheDocument()
  // 사는 쪽이 보는 카드라 "판매 가능" 이 아니라 "구매 가능" 이다.
  expect(screen.getByText(/구매 가능 전량/)).toBeInTheDocument()
})

// 사진이 생기기 전에도 자리를 잡아 둔다 — 나중에 카드 높이와 줄 맞춤이 통째로 달라지지 않게.
it('이미지 자리를 두고 그 위에 등급을 얹는다', () => {
  const { container } = render(
    <MemoryRouter>
      <ListingCard listing={{ ...listing, grade: 'A' }} />
    </MemoryRouter>,
  )

  expect(container.querySelector('.listing-card__thumbnail')).toBeInTheDocument()
  expect(screen.getByText('A등급')).toBeInTheDocument()
})
