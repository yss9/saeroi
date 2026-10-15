import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdminPage } from './AdminPage.tsx'

vi.mock('../../features/admin/components/CompanyRoleApprovalPanel.tsx', () => ({
  CompanyRoleApprovalPanel: () => <div>사업자 승인</div>,
}))

vi.mock('../../features/admin/components/PredictionMarketCreatePanel.tsx', () => ({
  PredictionMarketCreatePanel: () => <div>예측시장</div>,
}))

vi.mock('../../features/admin/blockchain/BlockchainVerificationPanel.tsx', () => ({
  BlockchainVerificationPanel: () => <div>블록체인 검증</div>,
}))

afterEach(() => {
  cleanup()
  delete window.ethereum
})

describe('AdminPage', () => {
  it('관리자 페이지에서 이미 연결된 MetaMask 계정을 인식한다', async () => {
    window.ethereum = {
      request: vi.fn(async ({ method }: { method: string }) => {
        if (method === 'eth_accounts') return ['0xd8da6bf26964af9d7eed9e03e53415d37aa96045']
        if (method === 'eth_chainId') return '0x539'
        return []
      }),
      on: vi.fn(),
      removeListener: vi.fn(),
    }

    render(<AdminPage section="applications" />)

    expect(await screen.findByText('0xd8dA…6045')).toBeInTheDocument()
  })
})
