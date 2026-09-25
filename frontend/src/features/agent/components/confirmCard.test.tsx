import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { ConfirmCard } from './ConfirmCard.tsx'
import type { PendingAction } from '../types/agent.types.ts'

/**
 * 확인 카드는 담당자가 **무엇에 동의하는지** 숫자와 이름으로 보인다.
 *
 * 투입 카드에 "소재 44 · 투입 5 kg = 제품 5 kg + 자투리 0 kg + 폐기 0 kg" 이 떴는데, 두 번째 소재는 좁은 칸으로
 * 밀려 글자가 세로로 쌓였고 소재는 번호로만 보였다. 제품 카드는 분류를 "WALLET" 같은 코드로 보였다.
 */
describe('ConfirmCard', () => {
  afterEach(cleanup)

  it('투입 카드는 소재 이름과 투입·제품 사용·자투리·폐기를 줄마다 보인다', () => {
    renderCard('add_production_inputs', {
      batchId: 8,
      inputs: [
        {
          materialId: 44, materialName: '가공 울 원단', inputQuantity: 5_000, productQuantity: 4_000,
          reusableScrapQuantity: 500, wasteQuantity: 500, wasteReason: '오염',
        },
        {
          materialId: 43, materialName: '폐데님 재생 원단', inputQuantity: 5_000, productQuantity: 5_000,
          reusableScrapQuantity: 0, wasteQuantity: 0,
        },
      ],
    })

    expect(screen.getByText('가공 울 원단')).toBeTruthy()
    expect(screen.getByText('폐데님 재생 원단')).toBeTruthy()
    expect(screen.getByText(/투입 5 ?kg = 제품 사용 4 ?kg \+ 자투리 0\.5 ?kg \+ 폐기 0\.5 ?kg/)).toBeTruthy()
    expect(screen.getByText('폐기 사유: 오염')).toBeTruthy()
    // 수동 투입 화면과 같은 안내다.
    expect(screen.getByText(/자투리는 생산을 확정할 때 새 소재로 등록되어 보유 소재로 돌아옵니다/)).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('서버가 풀어 두지 않았으면 들은 값으로 나머지를 계산한다', () => {
    renderCard('add_production_inputs', {
      batchId: 8,
      inputs: [{ materialId: 44, productQuantity: 4_000, reusableScrapQuantity: 500, wasteQuantity: 500 }],
    })

    expect(screen.getByText('소재 44')).toBeTruthy()
    expect(screen.getByText(/투입 5 ?kg = 제품 사용 4 ?kg/)).toBeTruthy()
  })

  it('제품에 쓰인 양이 없으면 수동 화면처럼 알린다', () => {
    renderCard('add_production_inputs', {
      batchId: 8,
      inputs: [{
        materialId: 44, materialName: '가공 울 원단', inputQuantity: 1_000, productQuantity: 0,
        reusableScrapQuantity: 1_000, wasteQuantity: 0,
      }],
    })

    expect(screen.getByRole('alert').textContent).toContain('제품에 쓰인 수량이 없습니다')
  })

  it('제품 카드는 분류를 한글로 보이고 바꿀 수 없다고 알린다', () => {
    renderCard('register_product_model', {
      name: '울·데님 업사이클 지갑', category: 'WALLET', price: 500_000, description: '울과 데님으로 만든 지갑',
    })

    expect(screen.getByText(/^지갑/)).toBeTruthy()
    expect(screen.queryByText(/WALLET/)).toBeNull()
    expect(screen.getByText(/등록 뒤 바꿀 수 없습니다/)).toBeTruthy()
  })

  it('가방은 세부 분류도 한글로 보인다', () => {
    renderCard('register_product_model', {
      name: '데님 토트백', category: 'BAG', subcategory: 'TOTE', price: 30_000, description: '토트백',
    })

    expect(screen.getByText(/가방 \/ 토트/)).toBeTruthy()
  })
})

function renderCard(toolName: string, payload: object) {
  const action: PendingAction = {
    id: 1,
    messageId: 1,
    actionType: 'CONFIRM',
    toolName,
    summary: '테스트',
    payloadJson: JSON.stringify(payload),
    resultJson: null,
    status: 'PENDING',
    stepTotal: 1,
    stepDone: 0,
    expiresAt: '2999-01-01T00:00:00',
  }
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ConfirmCard action={action} />
    </QueryClientProvider>,
  )
}
