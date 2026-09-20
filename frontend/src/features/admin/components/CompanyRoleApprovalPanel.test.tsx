import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompanyRoleApplication } from '../types/companyRoleApproval.types.ts'
import { CompanyRoleApprovalPanel } from './CompanyRoleApprovalPanel.tsx'

const api = vi.hoisted(() => ({
  getCompanyRoleApplications: vi.fn(),
  approveCompanyRole: vi.fn(),
  rejectCompanyRole: vi.fn(),
}))
const blockchain = vi.hoisted(() => ({ grantCompanyRoles: vi.fn() }))
vi.mock('../api/companyRoleApprovalApi.ts', () => api)
vi.mock('../../../services/blockchain/adminRoleApproval.ts', () => blockchain)

const pending: CompanyRoleApplication = {
  userId: 7,
  companyName: '순환 브랜드',
  businessNumber: '123-45-67890',
  representativeName: '김대표',
  email: 'brand@test.com',
  role: 'BRAND',
  walletAddress: '0x1111111111111111111111111111111111111111',
  walletVerified: true,
  status: 'PENDING',
  approvedWallet: null,
  transactionHashes: [],
  reviewedAt: null,
}

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><CompanyRoleApprovalPanel /></QueryClientProvider>)
}

describe('CompanyRoleApprovalPanel', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(cleanup)

  it('검증 지갑이 없으면 승인 버튼을 비활성화한다', async () => {
    api.getCompanyRoleApplications.mockResolvedValue([{ ...pending, walletAddress: null, walletVerified: false, status: 'WAITING_WALLET' }])
    renderPanel()
    expect(await screen.findByText('지갑 등록 대기')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '온체인 권한 확인 후 승인' })).toBeDisabled()
  })

  it('승인하면 대기 목록에서 처리한 카드를 제거한다', async () => {
    api.getCompanyRoleApplications.mockResolvedValue([pending])
    blockchain.grantCompanyRoles.mockResolvedValue(['0xaaa', '0xbbb', '0xccc'])
    api.approveCompanyRole.mockResolvedValue({
      ...pending,
      status: 'APPROVED',
      approvedWallet: pending.walletAddress,
      transactionHashes: ['0xaaa', '0xbbb', '0xccc'],
    })
    renderPanel()
    await userEvent.click(await screen.findByRole('button', { name: '온체인 권한 확인 후 승인' }))
    await waitFor(() => expect(blockchain.grantCompanyRoles).toHaveBeenCalledWith('BRAND', pending.walletAddress))
    expect(api.approveCompanyRole).toHaveBeenCalledWith(7, ['0xaaa', '0xbbb', '0xccc'])
    expect(await screen.findByText('심사할 사업자 계정이 없습니다.')).toBeInTheDocument()
  })

  it('온체인 역할이 이미 있으면 MetaMask 서명 없이 서버 승인을 완료한다', async () => {
    api.getCompanyRoleApplications.mockResolvedValue([pending])
    blockchain.grantCompanyRoles.mockResolvedValue([])
    api.approveCompanyRole.mockResolvedValue({
      ...pending,
      status: 'APPROVED',
      approvedWallet: pending.walletAddress,
    })
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: '온체인 권한 확인 후 승인' }))

    await waitFor(() => expect(api.approveCompanyRole).toHaveBeenCalledWith(7, []))
    expect(await screen.findByText('심사할 사업자 계정이 없습니다.')).toBeInTheDocument()
  })
})
