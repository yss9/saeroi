import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import type { PredictionMarket } from '../../features/prediction/types/prediction.types.ts'
import { PredictionMarketPage } from './PredictionMarketPage.tsx'

const prediction = vi.hoisted(() => ({ markets: [] as PredictionMarket[] }))

vi.mock('../../features/auth/hooks/useAuth.ts', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('../../features/auth/hooks/useLogout.ts', () => ({ useLogout: () => ({ mutate: vi.fn() }) }))
vi.mock('../../features/user/components/ConsumerAccountLayout.tsx', () => ({ ConsumerAccountLayout: ({ children }: { children: ReactNode }) => <main>{children}</main> }))
vi.mock('../../features/wallet/components/WalletRegistrationPanel.tsx', () => ({ WalletRegistrationPanel: () => null }))
vi.mock('../../features/wallet/hooks/useWallet.ts', () => ({
  useWallet: () => ({ status: 'connected', wrongChain: false, address: '0x123' }),
  useSwitchChainNotice: () => ({ message: null, report: () => {} }),
}))
vi.mock('../../features/wallet/hooks/useMyWallets.ts', () => ({ useMyWallets: () => ({ activeWallet: { walletAddress: '0x123' } }) }))
vi.mock('../../features/prediction/hooks/usePredictionMarkets.ts', () => ({ predictionKeys: { all: ['prediction-markets'] }, usePredictionMarkets: () => ({ data: prediction.markets }) }))
vi.mock('../../features/prediction/hooks/usePredictionChainState.ts', () => ({ usePredictionChainState: () => ({ data: undefined, refetch: vi.fn() }) }))
vi.mock('../../features/prediction/hooks/usePredictionPointBalance.ts', () => ({ predictionPointBalanceKey: () => ['balance'], usePredictionPointBalance: () => ({ data: 0n }) }))
vi.mock('../../features/prediction/api/predictionApi.ts', () => ({ grantPredictionWelcomePoint: () => Promise.reject(new Error('예측 포인트가 이미 지급되었습니다.')) }))

afterEach(() => {
  prediction.markets = []
  cleanup()
})

it('이미 지급된 참여 포인트는 오류 강조 대신 안내와 비활성 버튼을 표시한다', async () => {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><PredictionMarketPage /></MemoryRouter></QueryClientProvider>)
  fireEvent.click(screen.getByRole('button', { name: '첫 참여 포인트 받기' }))
  expect(await screen.findByText('참여 포인트를 이미 받았습니다.')).toHaveClass('prediction-notice')
  expect(screen.getByRole('button', { name: '참여 포인트 지급 완료' })).toBeDisabled()
})

it('종료된 시장의 결과는 사용자가 확인 버튼을 누른 뒤에만 표시한다', () => {
  prediction.markets = [{
    id: 1,
    chainMarketId: 1,
    productionBatchId: 1,
    productModelId: 1,
    productName: '데님 냉방',
    productThumbnailUrl: null,
    unitPrice: 10_000,
    productionQuantity: 3,
    brandWallet: '0x456',
    bettingEndsAt: '2026-09-21T10:00:00Z',
    salesStartsAt: '2026-09-21T10:00:00Z',
    salesEndsAt: '2026-09-21T11:00:00Z',
    status: 'RESOLVED',
    soldQuantity: 3,
    winningBucketIndex: 2,
    resultHash: '0xresult',
    buckets: [
      { index: 0, lowerBound: 0, upperBound: 1 },
      { index: 1, lowerBound: 2, upperBound: 2 },
      { index: 2, lowerBound: 3, upperBound: 3 },
    ],
  }]

  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><PredictionMarketPage /></MemoryRouter></QueryClientProvider>)

  expect(screen.queryByLabelText('최종 순판매량 3개, 당첨 구간 3')).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '결과 확인하기' }))
  expect(screen.getByLabelText('최종 순판매량 3개, 당첨 구간 3')).toBeInTheDocument()
})
