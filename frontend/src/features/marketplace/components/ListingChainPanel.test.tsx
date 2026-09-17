// 판매 온체인 등록 칸을 언제 그리는가.
//
// **평소에 나오면 안 된다.** 판매 등록(`ListingForm`)이 글을 만들면서 곧바로
// `createListing` 까지 서명하므로, 판매자가 자기 글을 열어 보는 대부분의 경우 이미
// 번호가 있다. 그때 남는 것은 제목·설명·번호뿐이라 — 버튼은 `!done` 일 때만 그려진다 —
// 자기 글이 어떻게 보이는지 확인하러 온 사람에게 할 일 없는 상자가 맨 아래에 붙는다.
//
// **그렇다고 없앨 수는 없다.** 등록 때 서명이 거절·실패하면 `chainListingId` 가 비고,
// 이 칸이 그것을 되살리는 유일한 수단이다. 되살리지 못하면 구매자가 온체인으로 주문할
// 수 없고, 그 글에서 생긴 거래는 발송·수령도 체인에 남지 않는다.

import { render, screen, cleanup } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Listing } from '../types/marketplace.types.ts'
import { ListingChainPanel } from './ListingChainPanel.tsx'

const SELLER_ID = 2

// 지갑·소재·체인 훅을 직접 자른다. 이 테스트가 보는 것은 "칸을 그리는가" 하나이고,
// 실제 훅을 끌고 오면 MetaMask 와 조회 캐시까지 준비해야 해 무엇을 검증하는지 흐려진다.
const auth = vi.hoisted(() => ({ user: { id: 2 } as { id: number } | null }))
vi.mock('../../auth/hooks/useAuth.ts', () => ({ useAuth: () => auth }))

const wallet = vi.hoisted(() => ({ address: '0xabc' as string | null }))
vi.mock('../../wallet/hooks/useWallet.ts', () => ({ useWallet: () => wallet }))

const material = vi.hoisted(() => ({ data: { tokenId: 7 } as { tokenId: number | null } | undefined }))
vi.mock('../../material/hooks/useMaterial.ts', () => ({ useMaterial: () => material }))

vi.mock('../hooks/useOnChainAvailable.ts', () => ({
  useOnChainAvailable: () => ({ data: BigInt(1_000_000) }),
}))

const chain = vi.hoisted(() => ({
  status: 'IDLE' as string,
  hash: null as string | null,
  error: null,
  isBusy: false,
  isRejected: false,
  register: vi.fn(),
}))
vi.mock('../hooks/useListingOnChain.ts', () => ({ useListingOnChain: () => chain }))

function listing(overrides: Partial<Listing> = {}): Listing {
  return {
    listingId: 9,
    chainListingId: null,
    materialId: 1,
    materialCode: 'MAT-000001',
    materialName: '세척된 폐데님',
    materialDescription: null,
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    grade: 'A',
    sellerId: SELLER_ID,
    sellerName: '수거업체 A',
    totalQuantity: 30_000,
    availableQuantity: 12_000,
    pricePerKg: 4_200,
    status: 'ON_SALE',
    createdAt: '2026-09-01T00:00:00.000Z',
    ...overrides,
  }
}

beforeEach(() => {
  auth.user = { id: SELLER_ID }
  wallet.address = '0xabc'
  material.data = { tokenId: 7 }
  chain.hash = null
})

afterEach(cleanup)

describe('판매 온체인 등록', () => {
  it('이미 등록된 판매 글에는 칸을 만들지 않는다', () => {
    const { container } = render(<ListingChainPanel listing={listing({ chainListingId: 4 })} />)

    // 자기 글을 확인하러 온 사람에게 누를 것도 할 일도 없는 칸이다.
    expect(container).toBeEmptyDOMElement()
  })

  it('등록이 빠진 판매 글에는 되살릴 수단을 남긴다', () => {
    render(<ListingChainPanel listing={listing({ chainListingId: null })} />)

    // 여기가 사라지면 이 글은 영영 체인에 오르지 못하고, 구매자도 온체인 주문을 못 한다.
    expect(screen.getByRole('button', { name: '온체인 등록' })).toBeEnabled()
  })

  it('방금 서명해 해시가 떠 있으면 결과를 남긴다', () => {
    chain.hash = '0x'.padEnd(66, 'a')

    render(<ListingChainPanel listing={listing({ chainListingId: 4 })} />)

    // 앞 테스트와 다른 것은 해시뿐이므로(둘 다 chainListingId 는 4), 이 쌍이 해시 조건만
    // 따로 못박는다. 서명이 끝나자마자 칸이 사라지면 성공했는지 알 수 없다.
    expect(screen.getByRole('heading', { name: '판매 온체인 등록' })).toBeInTheDocument()
  })

  it('판매자가 아니면 등록 여부와 무관하게 그리지 않는다', () => {
    auth.user = { id: 99 }

    const { container } = render(<ListingChainPanel listing={listing({ chainListingId: null })} />)

    expect(container).toBeEmptyDOMElement()
  })
})
