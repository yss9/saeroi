import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { PredictionMarketPage } from './PredictionMarketPage.tsx'

vi.mock('../../features/auth/hooks/useAuth.ts', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('../../features/auth/hooks/useLogout.ts', () => ({ useLogout: () => ({ mutate: vi.fn() }) }))
vi.mock('../../features/user/components/ConsumerAccountLayout.tsx', () => ({ ConsumerAccountLayout: ({ children }: { children: ReactNode }) => <main>{children}</main> }))
vi.mock('../../features/wallet/components/WalletRegistrationPanel.tsx', () => ({ WalletRegistrationPanel: () => null }))
vi.mock('../../features/wallet/hooks/useWallet.ts', () => ({ useWallet: () => ({ status: 'connected', wrongChain: false, address: '0x123' }) }))
vi.mock('../../features/wallet/hooks/useMyWallets.ts', () => ({ useMyWallets: () => ({ activeWallet: { walletAddress: '0x123' } }) }))
vi.mock('../../features/prediction/hooks/usePredictionMarkets.ts', () => ({ predictionKeys: { all: ['prediction-markets'] }, usePredictionMarkets: () => ({ data: [] }) }))
vi.mock('../../features/prediction/hooks/usePredictionPointBalance.ts', () => ({ predictionPointBalanceKey: () => ['balance'], usePredictionPointBalance: () => ({ data: 0n }) }))
vi.mock('../../features/prediction/api/predictionApi.ts', () => ({ grantPredictionWelcomePoint: () => Promise.reject(new Error('예측 포인트가 이미 지급되었습니다.')) }))

afterEach(cleanup)

it('이미 지급된 참여 포인트는 오류 강조 대신 안내와 비활성 버튼을 표시한다', async () => {
  render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><PredictionMarketPage /></MemoryRouter></QueryClientProvider>)
  fireEvent.click(screen.getByRole('button', { name: '첫 참여 포인트 받기' }))
  expect(await screen.findByText('참여 포인트를 이미 받았습니다.')).toHaveClass('prediction-notice')
  expect(screen.getByRole('button', { name: '참여 포인트 지급 완료' })).toBeDisabled()
})
