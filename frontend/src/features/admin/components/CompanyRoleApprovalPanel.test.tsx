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
vi.mock('../api/companyRoleApprovalApi.ts', () => api)

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
    expect(screen.getByRole('button', { name: '승인 및 역할 부여' })).toBeDisabled()
  })

  it('승인하면 응답의 온체인 반영 결과로 카드를 갱신한다', async () => {
    api.getCompanyRoleApplications.mockResolvedValue([pending])
    api.approveCompanyRole.mockResolvedValue({
      ...pending,
      status: 'APPROVED',
      approvedWallet: pending.walletAddress,
      transactionHashes: ['0xaaa', '0xbbb', '0xccc'],
    })
    renderPanel()
    await userEvent.click(await screen.findByRole('button', { name: '승인 및 역할 부여' }))
    await waitFor(() => expect(api.approveCompanyRole).toHaveBeenCalledWith(7, expect.anything()))
    expect(await screen.findByText('온체인 반영 3건 완료')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '승인 완료' })).toBeDisabled()
  })
})
