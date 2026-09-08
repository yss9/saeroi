// 구성 · 발생 사유 입력 (S15P21E104-437).
//
// 여기서 막는 거짓말 셋:
//   1) 화면에 보이지 않는 값을 서버로 보낸다 (신소재로 되돌렸는데 사유가 따라간다)
//   2) 덜 채운 빈 줄을 보내 등록이 400 으로 막힌다
//   3) 합계가 100 미만인 것을 오류처럼 보여준다
//
// MaterialForm.test.tsx 와 같은 모킹 경계(API 모듈 하나)를 쓴다 — 그래야
// react-hook-form 검증 → 값 변환 → 전송까지 실제로 통과한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MaterialForm } from './MaterialForm.tsx'

const api = vi.hoisted(() => ({ createMaterial: vi.fn() }))
vi.mock('../api/createMaterial.ts', () => api)

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MaterialForm />
    </QueryClientProvider>,
  )
}

async function fillRequired() {
  const user = userEvent.setup()
  await user.selectOptions(screen.getByLabelText('소재 유형'), 'DENIM')
  await user.type(screen.getByLabelText('소재명'), '폐데님 원단')
  await user.clear(screen.getByLabelText('수량 (kg)'))
  await user.type(screen.getByLabelText('수량 (kg)'), '20')
  return user
}

async function addFiber(user: ReturnType<typeof userEvent.setup>, index: number, fiber: string, percentage: string) {
  await user.click(screen.getByRole('button', { name: '섬유 추가' }))
  await user.selectOptions(screen.getByLabelText(`섬유 ${index}`), fiber)
  await user.type(screen.getByLabelText(`비율 ${index} (%)`), percentage)
}

function submitted() {
  expect(api.createMaterial).toHaveBeenCalledTimes(1)
  return api.createMaterial.mock.calls[0][0]
}

beforeEach(() => {
  api.createMaterial.mockReset()
  api.createMaterial.mockResolvedValue({ materialId: 42, materialCode: 'MAT-000042' })
})

afterEach(cleanup)

describe('발생 사유는 폐기물에만', () => {
  it('출처가 폐기물이면 사유 칸이 보인다', async () => {
    renderForm()

    // 기본값이 WASTE 다.
    expect(screen.getByLabelText('발생 사유 (선택)')).toBeInTheDocument()
  })

  it('출처를 신소재로 바꾸면 사유 칸이 사라진다', async () => {
    // 신소재에 사유를 보내면 서버가 400 을 낸다. 칸을 감춰 그 오류를 없앤다.
    renderForm()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByLabelText('출처'), 'VIRGIN')

    expect(screen.queryByLabelText('발생 사유 (선택)')).not.toBeInTheDocument()
  })

  it('사유를 고른 뒤 신소재로 되돌리면 사유를 보내지 않는다', async () => {
    // 칸이 감춰져도 폼 값에는 남는다. 그대로 보내면 화면에 보이지 않는 값 때문에
    // 등록이 막힌다 — 사용자가 원인을 알 방법이 없다.
    renderForm()
    const user = await fillRequired()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'DEADSTOCK')
    await user.selectOptions(screen.getByLabelText('출처'), 'VIRGIN')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).not.toHaveProperty('originReason')
  })

  it('고른 사유를 전송한다', async () => {
    renderForm()
    const user = await fillRequired()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'OFFCUT')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted().originReason).toBe('OFFCUT')
  })

  it('사유를 고르지 않아도 등록된다', async () => {
    // 서버가 아직 필수로 요구하지 않는다. 여기서 먼저 막으면 등록이 불가능해진다.
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).not.toHaveProperty('originReason')
  })
})

describe('기타 사유는 설명을 요구한다', () => {
  it('기타를 고르면 설명 칸이 나타난다', async () => {
    renderForm()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'ETC')

    expect(screen.getByLabelText('발생 사유 설명')).toBeInTheDocument()
  })

  it('기타인데 설명이 비면 전송하지 않는다', async () => {
    // ETC 는 값 자체가 뜻을 담지 않아 설명이 없으면 분류가 무의미해진다.
    renderForm()
    const user = await fillRequired()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'ETC')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    expect(await screen.findByText('기타를 선택하면 설명이 필요합니다.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('기타와 설명을 함께 전송한다', async () => {
    renderForm()
    const user = await fillRequired()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'ETC')
    await user.type(screen.getByLabelText('발생 사유 설명'), '행사 후 남은 배너 원단')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).toMatchObject({
      originReason: 'ETC',
      originReasonNote: '행사 후 남은 배너 원단',
    })
  })

  it('기타가 아닌 사유에 붙은 설명도 함께 보낸다', async () => {
    // 부가 설명은 어느 값에서도 쓸모가 있다. 버리면 사용자가 적어 둔 정보가 사라진다.
    renderForm()
    const user = await fillRequired()

    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'ETC')
    await user.type(screen.getByLabelText('발생 사유 설명'), '2024 F/W 재고')
    await user.selectOptions(screen.getByLabelText('발생 사유 (선택)'), 'DEADSTOCK')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).toMatchObject({
      originReason: 'DEADSTOCK',
      originReasonNote: '2024 F/W 재고',
    })
  })
})

describe('구성', () => {
  it('구성 없이도 등록된다', async () => {
    // 구성은 선택 정보다. 필수로 만들면 사용자가 아무 값이나 채워 넣는다.
    renderForm()
    const user = await fillRequired()

    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted()).not.toHaveProperty('composition')
  })

  it('입력한 줄을 전송한다', async () => {
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')
    await addFiber(user, 2, 'POLYESTER', '40')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted().composition).toEqual([
      { fiberType: 'COTTON', percentage: 60 },
      { fiberType: 'POLYESTER', percentage: 40 },
    ])
  })

  it('합계가 100 미만인 것을 거절하지 않는다', async () => {
    // "면 60% 는 확실하고 나머지는 모른다" 가 흔하다. 100 을 강제하면 사용자가
    // "기타 40%" 를 지어내고 그 값이 Upcycle Ratio 집계로 흘러간다.
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted().composition).toEqual([{ fiberType: 'COTTON', percentage: 60 }])
  })

  it('합계를 보여주고 미확인 부분을 밝힌다', async () => {
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')

    expect(await screen.findByText(/합계 60%/)).toBeInTheDocument()
    expect(screen.getByText(/나머지 미확인/)).toBeInTheDocument()
  })

  it('합계가 100 을 넘으면 알린다', async () => {
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')
    await addFiber(user, 2, 'POLYESTER', '41')

    expect(await screen.findByText(/100%를 넘을 수 없습니다/)).toBeInTheDocument()
  })

  it('줄을 삭제할 수 있다', async () => {
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')
    await user.click(screen.getByRole('button', { name: '섬유 1 삭제' }))

    expect(screen.queryByLabelText('섬유 1')).not.toBeInTheDocument()
  })

  it('덜 채운 빈 줄은 보내지 않는다', async () => {
    // "섬유 추가" 를 누르고 아무것도 고르지 않은 상태가 정상 흐름이다.
    // 그 줄을 보내면 서버가 400 을 낸다.
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '60')
    await user.click(screen.getByRole('button', { name: '섬유 추가' }))
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    // 빈 줄은 폼 검증에 걸린다 — 그게 먼저 사용자에게 보이는 것이 맞다.
    expect(await screen.findByText('섬유 종류를 선택해 주세요.')).toBeInTheDocument()
    expect(api.createMaterial).not.toHaveBeenCalled()
  })

  it('소수 비율을 그대로 보낸다', async () => {
    renderForm()
    const user = await fillRequired()

    await addFiber(user, 1, 'COTTON', '33.33')
    await user.click(screen.getByRole('button', { name: '소재 등록' }))

    await waitFor(() => expect(api.createMaterial).toHaveBeenCalled())
    expect(submitted().composition).toEqual([{ fiberType: 'COTTON', percentage: 33.33 }])
  })
})
