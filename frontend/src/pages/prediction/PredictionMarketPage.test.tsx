import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { PredictionChainState, PredictionMarket } from '../../features/prediction/types/prediction.types.ts'
import { PredictionMarketPage } from './PredictionMarketPage.tsx'

const POINT = 1_000000000000000000n

const prediction = vi.hoisted(() => ({
  markets: [] as PredictionMarket[],
  chainState: undefined as PredictionChainState | undefined,
  balance: 0n as bigint,
  placeBet: vi.fn(),
  saveSurvey: vi.fn(),
}))

vi.mock('../../features/auth/hooks/useAuth.ts', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('../../features/auth/hooks/useLogout.ts', () => ({ useLogout: () => ({ mutate: vi.fn() }) }))
vi.mock('../../features/user/components/ConsumerAccountLayout.tsx', () => ({ ConsumerShopLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }))
vi.mock('../../features/wallet/components/WalletRegistrationPanel.tsx', () => ({ WalletRegistrationPanel: () => null }))
vi.mock('../../features/wallet/hooks/useWallet.ts', () => ({
  useWallet: () => ({ status: 'connected', wrongChain: false, address: '0x123' }),
  useSwitchChainNotice: () => ({ message: null, report: () => {} }),
}))
vi.mock('../../features/wallet/hooks/useMyWallets.ts', () => ({ useMyWallets: () => ({ activeWallet: { walletAddress: '0x123' } }) }))
vi.mock('../../features/prediction/hooks/usePredictionMarkets.ts', () => ({ predictionKeys: { all: ['prediction-markets'] }, usePredictionMarkets: () => ({ data: prediction.markets }) }))
vi.mock('../../features/prediction/hooks/usePredictionChainState.ts', () => ({ usePredictionChainState: () => ({ data: prediction.chainState, refetch: vi.fn() }) }))
vi.mock('../../features/prediction/hooks/usePredictionPointBalance.ts', () => ({ predictionPointBalanceKey: () => ['balance'], usePredictionPointBalance: () => ({ data: prediction.balance }) }))
vi.mock('../../features/prediction/api/predictionApi.ts', () => ({
  grantPredictionWelcomePoint: () => Promise.reject(new Error('예측 포인트가 이미 지급되었습니다.')),
  savePredictionSurvey: prediction.saveSurvey,
}))
vi.mock('../../features/prediction/services/predictionContractService.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../features/prediction/services/predictionContractService.ts')>()),
  approveAndPlacePredictionBet: prediction.placeBet,
}))

function market(overrides: Partial<PredictionMarket> = {}): PredictionMarket {
  return {
    id: 1, chainMarketId: 1, productionBatchId: 1, productModelId: 1,
    productName: '데님 토트백', productThumbnailUrl: null, unitPrice: 10_000, productionQuantity: 10,
    brandWallet: '0x456',
    bettingEndsAt: '2099-01-01T00:00:00Z', salesStartsAt: '2099-01-01T00:00:00Z', salesEndsAt: '2099-01-08T00:00:00Z',
    status: 'OPEN', soldQuantity: null, winningBucketIndex: null, resultHash: null,
    buckets: [
      { index: 0, lowerBound: 0, upperBound: 3 },
      { index: 1, lowerBound: 4, upperBound: 6 },
      { index: 2, lowerBound: 7, upperBound: 10 },
    ],
    ...overrides,
  }
}

function chainState(overrides: Partial<PredictionChainState> = {}): PredictionChainState {
  return {
    totalPool: 40n * POINT, userStake: 0n, userWinningStake: 0n, winningPool: 0n,
    claimed: false, refundOnly: false,
    bucketPools: [10n * POINT, 30n * POINT, 0n], userBucketStakes: [0n, 0n, 0n],
    ...overrides,
  }
}

function renderPage() {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><PredictionMarketPage /></MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
  prediction.balance = 100n * POINT
  prediction.placeBet.mockResolvedValue({ transactionHash: '0x' + 'a'.repeat(64), stakeAmount: String(10n * POINT) })
  prediction.saveSurvey.mockResolvedValue({})
})

afterEach(() => {
  prediction.markets = []
  prediction.chainState = undefined
  vi.clearAllMocks()
  cleanup()
})

it('이미 지급된 참여 포인트는 오류 강조 대신 안내와 비활성 버튼을 표시한다', async () => {
  prediction.balance = 0n
  renderPage()
  fireEvent.click(screen.getByRole('button', { name: '첫 참여 포인트 받기' }))
  expect(await screen.findByText('참여 포인트를 이미 받았습니다.')).toHaveClass('prediction-notice')
  expect(screen.getByRole('button', { name: '참여 포인트 지급 완료' })).toBeDisabled()
})

// 포인트가 있는 사람에게 늘 보이면 누를 때마다 "이미 지급" 으로 거절당한다.
it('포인트가 있으면 첫 참여 포인트 받기를 권하지 않는다', () => {
  renderPage()
  expect(screen.queryByRole('button', { name: '첫 참여 포인트 받기' })).not.toBeInTheDocument()
})

it('참여할 수 있는 시장과 지난 시장을 나누고 상태를 한국어로 보인다', () => {
  prediction.markets = [
    market({ id: 1, productName: '진행 중 가방' }),
    market({ id: 2, productName: '집계 중 지갑', bettingEndsAt: '2020-01-01T00:00:00Z' }),
  ]
  renderPage()

  const open = screen.getByRole('region', { name: '참여할 수 있는 예측' })
  const past = screen.getByRole('region', { name: '지난 예측' })
  expect(within(open).getByText('진행 중 가방')).toBeInTheDocument()
  expect(within(open).getByText('예측 진행 중')).toBeInTheDocument()
  // 서버는 마감이 지나도 OPEN 이다. 그대로 보이면 "진행 중" 이라면서 참여할 수 없다.
  expect(within(past).getByText('판매 집계 중')).toBeInTheDocument()
  expect(within(past).queryByRole('button', { name: '예측 참여하기' })).not.toBeInTheDocument()
})

// 첫 구간과 첫 이유가 골라진 채로 열리면 아무것도 고르지 않은 예측이 기록된다.
it('구간과 이유를 직접 고르기 전에는 참여할 수 없다', () => {
  prediction.markets = [market()]
  prediction.chainState = chainState()
  renderPage()

  const submit = screen.getByRole('button', { name: '예측 참여하기' })
  expect(submit).toBeDisabled()
  fireEvent.click(screen.getByLabelText(/4~6개/))
  expect(submit).toBeDisabled()
  fireEvent.click(screen.getByLabelText('디자인'))
  expect(submit).toBeEnabled()
})

it('구간마다 다른 참여자가 건 비율을 보여준다', () => {
  prediction.markets = [market()]
  prediction.chainState = chainState()
  renderPage()

  expect(screen.getByLabelText('참여 포인트의 25%')).toBeInTheDocument()
  expect(screen.getByLabelText('참여 포인트의 75%')).toBeInTheDocument()
})

it('보유 포인트보다 많이 걸 수 없다', () => {
  prediction.markets = [market()]
  prediction.chainState = chainState()
  renderPage()

  fireEvent.click(screen.getByLabelText(/4~6개/))
  fireEvent.click(screen.getByLabelText('디자인'))
  fireEvent.change(screen.getByLabelText('걸 포인트'), { target: { value: '500' } })

  expect(screen.getByText('보유 포인트(100)보다 많이 걸 수 없습니다.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '예측 참여하기' })).toBeDisabled()
})

it('참여하면 폼을 내리고 내 예측을 보인다 — 같은 시장에 두 번 걸지 않게', async () => {
  prediction.markets = [market()]
  prediction.chainState = chainState()
  renderPage()

  fireEvent.click(screen.getByLabelText(/4~6개/))
  fireEvent.click(screen.getByLabelText('가격'))
  fireEvent.click(screen.getByRole('button', { name: '예측 참여하기' }))

  await waitFor(() => expect(screen.queryByRole('button', { name: '예측 참여하기' })).not.toBeInTheDocument())
  expect(prediction.placeBet).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }), 1, '10', expect.any(Function))
  expect(prediction.saveSurvey).toHaveBeenCalledWith(1, expect.objectContaining({ bucketIndex: 1, reasonCode: 'PRICE' }))
  expect(screen.getByText('내 예측')).toBeInTheDocument()
})

it('이미 참여한 시장은 폼 대신 내가 건 구간과 포인트를 보인다', () => {
  prediction.markets = [market()]
  prediction.chainState = chainState({ userStake: 10n * POINT, userBucketStakes: [0n, 10n * POINT, 0n] })
  renderPage()

  expect(screen.queryByRole('button', { name: '예측 참여하기' })).not.toBeInTheDocument()
  expect(screen.getByText('4~6개')).toBeInTheDocument()
  expect(screen.getByText(/10 포인트/)).toBeInTheDocument()
})

// 체인에는 기록됐는데 이유 저장만 실패했다. 실패라고 말하면 다시 걸어 포인트를 또 잃는다.
it('이유 저장만 실패하면 다시 참여하지 않아도 된다고 알린다', async () => {
  prediction.saveSurvey.mockRejectedValue(new Error('server down'))
  prediction.markets = [market()]
  prediction.chainState = chainState()
  renderPage()

  fireEvent.click(screen.getByLabelText(/4~6개/))
  fireEvent.click(screen.getByLabelText('가격'))
  fireEvent.click(screen.getByRole('button', { name: '예측 참여하기' }))

  expect(await screen.findByText(/다시 참여하지 않아도 됩니다/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '예측 참여하기' })).not.toBeInTheDocument()
})

it('종료된 시장의 결과는 확인 버튼을 누른 뒤에 보이고, 맞혔으면 받을 포인트를 알린다', () => {
  prediction.chainState = chainState({
    totalPool: 30n * POINT, userStake: 10n * POINT, userWinningStake: 10n * POINT, winningPool: 10n * POINT,
    claimed: false, bucketPools: [10n * POINT, 10n * POINT, 10n * POINT], userBucketStakes: [0n, 0n, 10n * POINT],
  })
  prediction.markets = [market({
    status: 'RESOLVED', soldQuantity: 8, winningBucketIndex: 2, resultHash: '0xresult',
    bettingEndsAt: '2026-09-21T10:00:00Z', salesStartsAt: '2026-09-21T10:00:00Z', salesEndsAt: '2026-09-21T11:00:00Z',
  })]
  renderPage()

  expect(screen.queryByLabelText(/최종 순판매량/)).not.toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '결과 확인하기' }))
  expect(screen.getByLabelText('최종 순판매량 8개, 당첨 구간 7~10개')).toBeInTheDocument()
  expect(screen.getByText(/맞혔어요! 10 포인트를 걸어 30 포인트를 받습니다/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '30 포인트 받기' })).toBeInTheDocument()
})
