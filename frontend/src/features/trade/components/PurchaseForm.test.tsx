// 부분 구매 폼 검증.
//
// 이 도메인에서 사고가 나면 안 되는 지점은 하나다 — **판매 가능 수량 초과 구매**.
// 넘겨서 요청하면 서버가 거절하지만, 그 전에 화면이 막아야 사용자가 결제까지 갔다가
// 실패하는 일이 없다.
//
// 금액 계산도 함께 본다. 화면에 보인 금액과 실제 결제 금액이 다르면 신뢰가 깨진다.
//
// 배송지도 이 폼의 책임이다. **배송지 없이 만들어진 거래는 발송·수령이 서버에서 404 로
// 막혀 판매자도 구매자도 끝낼 수 없다** — 그래서 빠뜨린 채 요청이 나가지 않아야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Listing } from '../../marketplace/types/marketplace.types.ts'
import { PurchaseForm } from './PurchaseForm.tsx'

const api = vi.hoisted(() => ({ createTrade: vi.fn() }))
vi.mock('../api/createTrade.ts', () => api)

/**
 * 우편번호 검색을 모킹한다. 실제 구현은 카카오 CDN 스크립트를 주입하고 팝업을 띄우므로
 * 테스트 환경에서는 뜨지 않는다.
 *
 * **주소 칸이 `readOnly` 가 되었다.** 택배 송장에 인쇄될 값이라 오타가 그대로 배송
 * 사고가 되고, 우편번호와 주소가 서로 다른 곳을 가리켜도 폼은 그것을 알 수 없다.
 * 그래서 타이핑이 아니라 검색으로만 채운다 — 가입·배송지 화면과 같은 판단이다.
 */
const postcode = vi.hoisted(() => ({ openKakaoPostcode: vi.fn() }))
vi.mock('../../../utils/kakaoPostcode.ts', () => postcode)

const LISTING: Listing = {
  listingId: 1,
  chainListingId: null,
  materialId: 1,
  materialCode: 'MAT-000001',
  materialName: '세척된 폐데님',
  materialDescription: null,
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  generationType: 'INITIAL',
  grade: 'A',
  sellerId: 2,
  sellerName: '수거업체 A',
  totalQuantity: 30_000,
  availableQuantity: 12_000, // 12kg 남음
  pricePerKg: 4_200,
  status: 'ON_SALE',
  createdAt: '2026-09-01T00:00:00.000Z',
}

function renderForm(listing: Listing = LISTING) {
  // 테스트마다 새 QueryClient 를 쓴다. 캐시가 테스트 사이에 새면 순서에 따라 결과가 달라진다.
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PurchaseForm listing={listing} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.createTrade.mockResolvedValue({ tradeId: 42 })
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

/** 배송지를 모두 채운다. 이 폼은 배송지가 없으면 요청을 보내지 않는다. */
async function fillAddress(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('수취인'), '브랜드 B 자재팀')
  await user.type(screen.getByLabelText('연락처'), '010-0000-0000')
  // 검색 버튼을 눌러 채운다. 두 칸은 readOnly 라 타이핑으로는 들어가지 않는다.
  postcode.openKakaoPostcode.mockImplementationOnce(
    async (onComplete: (s: { zonecode: string; address: string }) => void) => {
      onComplete({ zonecode: '06236', address: '서울 강남구 테헤란로 1' })
    },
  )
  await user.click(screen.getByRole('button', { name: '주소 검색' }))
}

describe('PurchaseForm', () => {
  it('수량을 입력하기 전에는 구매할 수 없다', () => {
    renderForm()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeDisabled()
  })

  it('입력한 수량으로 결제 금액을 계산한다', async () => {
    const user = userEvent.setup()
    renderForm()

    // 10kg × 4,200원/kg = 42,000원
    await user.type(screen.getByLabelText('구매 수량'), '10')

    expect(screen.getByText('42,000원')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeEnabled()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('판매 가능 수량을 넘으면 막고 이유를 알린다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '13') // 12kg 만 남았다

    expect(screen.getByRole('alert')).toHaveTextContent('판매 가능 수량')
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeDisabled()
  })

  it('남은 수량과 정확히 같으면 구매할 수 있다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '12')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매 요청' })).toBeEnabled()
  })

  // 소수점 수량은 gram 변환에서 부동소수점 오차가 날 수 있는 지점이다.
  it('소수점 수량도 정확히 계산한다', async () => {
    const user = userEvent.setup()
    renderForm()

    // 2.3kg → 2300g × 4,200원/kg = 9,660원
    await user.type(screen.getByLabelText('구매 수량'), '2.3')

    expect(screen.getByText('9,660원')).toBeInTheDocument()
  })

  // 배송지를 빠뜨린 거래는 아무도 끝낼 수 없다. 서버도 400 으로 막지만, 그 전에
  // 어느 칸이 비었는지 화면이 알려줘야 한다.
  it('배송지를 비운 채로는 구매를 요청하지 않는다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '10')
    await user.click(screen.getByRole('button', { name: '구매 요청' }))

    expect(await screen.findByText('수취인 이름을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('주소를 검색해 주세요.')).toBeInTheDocument()
    expect(api.createTrade).not.toHaveBeenCalled()
  })

  it('수량과 배송지를 함께 보낸다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '10')
    await fillAddress(user)
    await user.click(screen.getByRole('button', { name: '구매 요청' }))

    // 첫 인자만 본다. useCreateTrade 가 createTrade 를 mutationFn 으로 그대로 넘기므로
    // TanStack Query 가 두 번째 인자로 자기 컨텍스트({ client, meta, mutationKey })를 준다.
    expect(api.createTrade.mock.calls[0][0]).toEqual({
      listingId: 1,
      // 10kg → 10,000g. 금액은 보내지 않는다 — 서버가 계산한다.
      quantity: 10_000,
      unit: 'G',
      // 화면은 기본 경로인 카카오페이를 보낸다. 확정은 서버가 승인 시점에 덮어쓴다 —
      // 어느 게이트웨이가 청구했는지는 설정이 정하므로 화면이 단정할 수 없다.
      paymentMethod: 'KAKAO_PAY',
      receiverName: '브랜드 B 자재팀',
      receiverPhone: '010-0000-0000',
      zipCode: '06236',
      address: '서울 강남구 테헤란로 1',
      // 상세 주소는 비웠으므로 필드 자체가 없다.
    })
  })

  it('상세 주소를 채우면 함께 보낸다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(screen.getByLabelText('구매 수량'), '10')
    await fillAddress(user)
    await user.type(screen.getByLabelText('상세 주소'), '3층')
    await user.click(screen.getByRole('button', { name: '구매 요청' }))

    expect(api.createTrade.mock.calls[0][0]).toMatchObject({ addressDetail: '3층' })
  })

  it('판매가 끝난 소재는 구매 버튼이 잠긴다', () => {
    renderForm({ ...LISTING, status: 'SOLD_OUT', availableQuantity: 0 })
    expect(screen.getByRole('button', { name: '판매가 종료된 소재입니다' })).toBeDisabled()
  })
})

/**
 * <b>주소는 검색으로만 채운다.</b>
 *
 * 이 화면만 직접 타이핑으로 남아 있었다 — 가입·배송지·가공업체 등록은 모두 카카오
 * 우편번호 검색을 쓴다. 소재를 사는 브랜드가 다른 화면과 다른 방식으로 주소를 넣게
 * 되고, 그쪽에서 걸러지던 오타가 여기서는 그대로 통과해 송장에 인쇄됐다.
 */
describe('주소 검색', () => {
  it('우편번호와 주소는 직접 입력할 수 없다', () => {
    // readOnly 가 풀리면 검증이 의미를 잃는다 — 우편번호와 주소가 서로 다른 곳을
    // 가리켜도 폼은 알 수 없다.
    renderForm()

    expect(screen.getByLabelText('우편번호')).toHaveAttribute('readonly')
    expect(screen.getByLabelText('주소')).toHaveAttribute('readonly')
  })

  it('검색 결과가 두 칸을 함께 채운다', async () => {
    renderForm()
    const user = userEvent.setup()
    postcode.openKakaoPostcode.mockImplementationOnce(
      async (onComplete: (s: { zonecode: string; address: string }) => void) => {
        onComplete({ zonecode: '13529', address: '경기 성남시 분당구 판교역로 235' })
      },
    )

    await user.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(screen.getByLabelText('우편번호')).toHaveValue('13529')
    expect(screen.getByLabelText('주소')).toHaveValue('경기 성남시 분당구 판교역로 235')
  })

  it('검색이 실패하면 그 사실을 알린다', async () => {
    // 두 칸이 읽기 전용이라, 실패를 말해 주지 않으면 사용자가 손으로 메울 수도 없이
    // 막힌다. 조용히 아무 일도 안 일어나는 것이 가장 나쁘다.
    renderForm()
    const user = userEvent.setup()
    postcode.openKakaoPostcode.mockRejectedValueOnce(new Error('script blocked'))

    await user.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('주소 검색을 불러오지 못했어요')
  })
})
