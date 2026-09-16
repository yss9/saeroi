// 배송지 주소록.
//
// **택배가 실제로 가는 곳을 사용자가 손으로 관리하는 화면이다.** 틀리면 안 되는 지점 넷.
//
//   1) **빈 목록과 실패를 구분한다.** 주소를 받기 전에 가입한 계정은 0건이 정상이고,
//      서버도 그때 에러가 아니라 []를 준다 (ARCHITECTURE §8).
//   2) **기본 배송지에는 "기본으로 지정" 을 두지 않는다.** 눌러도 아무 일이 없는 버튼은
//      화면이 멈춘 것처럼 읽힌다.
//   3) **삭제는 확인을 거친다.** 되돌릴 수 없다.
//   4) **20건에서 막힌다는 것을 미리 알린다.** 서버가 422 로 거절하는데, 주소를 다 적고
//      저장을 눌러야 알게 되면 그 입력이 통째로 헛수고다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ConsumerAddress } from '../types/consumerAddress.types.ts'
import { ConsumerAddressBook } from './ConsumerAddressBook.tsx'

const api = vi.hoisted(() => ({
  getConsumerAddresses: vi.fn(),
  addConsumerAddress: vi.fn(),
  updateConsumerAddress: vi.fn(),
  deleteConsumerAddress: vi.fn(),
  setDefaultConsumerAddress: vi.fn(),
}))
vi.mock('../api/consumerAddressApi.ts', () => api)

// 주소 검색은 카카오 스크립트를 불러와 팝업을 띄운다. 고른 주소가 폼에 들어오는지만 본다.
const openKakaoPostcode = vi.hoisted(() =>
  vi.fn((onComplete: (selection: { zonecode: string; address: string }) => void) => {
    onComplete({ zonecode: '06236', address: '서울특별시 강남구 테헤란로 212' })
    return Promise.resolve()
  }),
)
vi.mock('../../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode }))

function address(overrides: Partial<ConsumerAddress> = {}): ConsumerAddress {
  return {
    addressId: 1,
    alias: '집',
    receiverName: '김새로',
    receiverPhone: '010-1234-5678',
    zipCode: '06236',
    address: '서울특별시 강남구 테헤란로 212',
    addressDetail: '101동 1004호',
    isDefault: true,
    ...overrides,
  }
}

function renderBook() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ConsumerAddressBook />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getConsumerAddresses.mockResolvedValue([address()])
  api.addConsumerAddress.mockResolvedValue(address({ addressId: 2 }))
  api.updateConsumerAddress.mockResolvedValue(address())
  api.deleteConsumerAddress.mockResolvedValue(undefined)
  api.setDefaultConsumerAddress.mockResolvedValue(address())
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ConsumerAddressBook', () => {
  it('등록된 배송지를 보여준다', async () => {
    renderBook()

    expect(await screen.findByText('집')).toBeInTheDocument()
    expect(screen.getByText('김새로 · 010-1234-5678')).toBeInTheDocument()
    expect(screen.getByText('[06236] 서울특별시 강남구 테헤란로 212')).toBeInTheDocument()
    expect(screen.getByText('101동 1004호')).toBeInTheDocument()
  })

  // 주소를 받기 전에 가입한 계정이다. 에러가 아니다.
  it('배송지가 없으면 등록으로 데려간다', async () => {
    api.getConsumerAddresses.mockResolvedValue([])
    const user = renderBook()

    expect(await screen.findByText('등록된 배송지가 없습니다')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '첫 배송지 등록하기' }))

    expect(screen.getByRole('heading', { name: '새 배송지' })).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다 — 눌러도 아무 일이 없는 버튼을 두지 않는다.
  it('기본 배송지에는 기본 지정 버튼을 두지 않는다', async () => {
    api.getConsumerAddresses.mockResolvedValue([
      address({ addressId: 1, alias: '집', isDefault: true }),
      address({ addressId: 2, alias: '회사', isDefault: false }),
    ])
    renderBook()

    const home = (await screen.findByText('집')).closest('article') as HTMLElement
    const office = screen.getByText('회사').closest('article') as HTMLElement

    expect(within(home).getByText('기본 배송지')).toBeInTheDocument()
    expect(within(home).queryByRole('button', { name: '기본으로 지정' })).not.toBeInTheDocument()
    expect(within(office).getByRole('button', { name: '기본으로 지정' })).toBeInTheDocument()
  })

  it('기본 배송지를 바꾼다', async () => {
    api.getConsumerAddresses.mockResolvedValue([
      address({ addressId: 1, isDefault: true }),
      address({ addressId: 2, alias: '회사', isDefault: false }),
    ])
    const user = renderBook()
    await screen.findByText('회사')

    await user.click(screen.getByRole('button', { name: '기본으로 지정' }))

    // v5 는 mutationFn 에 컨텍스트를 두 번째 인자로 넘긴다. 첫 인자만 본다.
    await waitFor(() => expect(api.setDefaultConsumerAddress).toHaveBeenCalled())
    expect(api.setDefaultConsumerAddress.mock.calls[0][0]).toBe(2)
  })

  // 되돌릴 수 없다. 어느 배송지인지 확인시킨다.
  it('삭제는 확인을 거치고 어느 배송지인지 문장에 넣는다', async () => {
    const user = renderBook()
    await screen.findByText('집')

    await user.click(screen.getByRole('button', { name: '삭제' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/집\(김새로\)/)).toBeInTheDocument()
    expect(api.deleteConsumerAddress).not.toHaveBeenCalled()

    await user.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(api.deleteConsumerAddress).toHaveBeenCalled())
    expect(api.deleteConsumerAddress.mock.calls[0][0]).toBe(1)
  })

  it('새 배송지를 등록한다', async () => {
    api.getConsumerAddresses.mockResolvedValue([])
    const user = renderBook()
    await screen.findByText('등록된 배송지가 없습니다')

    await user.click(screen.getByRole('button', { name: '첫 배송지 등록하기' }))
    await user.type(screen.getByLabelText('배송지 이름'), '회사')
    await user.type(screen.getByLabelText('수령인'), '김새로')
    await user.type(screen.getByLabelText('수령인 연락처'), '01012345678')
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    await user.type(await screen.findByLabelText('상세 주소'), '5층')
    await user.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(api.addConsumerAddress).toHaveBeenCalled())
    expect(api.addConsumerAddress.mock.calls[0][0]).toEqual({
      alias: '회사',
      receiverName: '김새로',
      receiverPhone: '010-1234-5678',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '5층',
    })
  })

  // 주소를 다 적고 저장을 눌러야 한계를 알게 되면 그 입력이 통째로 헛수고다.
  it('20건이 차면 추가 버튼 대신 이유를 보여준다', async () => {
    api.getConsumerAddresses.mockResolvedValue(
      Array.from({ length: 20 }, (_, index) =>
        address({ addressId: index + 1, alias: `주소${index + 1}`, isDefault: index === 0 }),
      ),
    )
    renderBook()

    expect(await screen.findByText(/배송지는 20개까지 등록할 수 있습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '새 배송지 추가' })).not.toBeInTheDocument()
  })

  it('조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getConsumerAddresses.mockRejectedValue(
      createAppError(ERROR_KIND.NETWORK_ERROR, { message: '서버에 연결할 수 없습니다.' }),
    )
    renderBook()

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
