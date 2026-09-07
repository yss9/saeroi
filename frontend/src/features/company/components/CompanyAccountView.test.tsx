import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import type { CompanyAccountData } from '../types/companyAccount.types.ts'
import { CompanyAccountView } from './CompanyAccountView.tsx'

const company: CompanyAccountData = {
  companyName: '새로이 브랜드',
  email: 'brand@saeroi.test',
  role: 'BRAND',
  businessNumber: '123-45-67890',
  representativeName: '김새로',
  companyPhone: '02-1234-5678',
  companyAddress: '서울특별시 강남구 테헤란로 212',
  status: 'ACTIVE',
  walletAddress: null,
}

afterEach(cleanup)

function renderView(children: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(children, {
    wrapper: ({ children: wrappedChildren }) => (
      <QueryClientProvider client={queryClient}>{wrappedChildren}</QueryClientProvider>
    ),
  })
}

describe('CompanyAccountView', () => {
  it('renders company identity and registered business information', () => {
    renderView(<CompanyAccountView state={{ status: 'ready', data: company }} />)

    expect(screen.getByRole('heading', { name: '회사 정보', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '새로이 브랜드' })).toBeInTheDocument()
    expect(screen.getAllByText('브랜드').length).toBeGreaterThan(0)
    expect(screen.getByText('123-45-67890')).toBeInTheDocument()
    expect(screen.getByText('김새로')).toBeInTheDocument()
    expect(screen.getByText('활성 계정')).toBeInTheDocument()
  })

  it('shows wallet registration entry and a registered address', () => {
    const { rerender } = renderView(<CompanyAccountView state={{ status: 'ready', data: company }} />)

    expect(screen.getByText('아직 연결된 지갑이 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'MetaMask 설치하기' })).toHaveAttribute(
      'href',
      'https://metamask.io/download/',
    )

    rerender(
      <CompanyAccountView
        state={{ status: 'ready', data: { ...company, walletAddress: '0x1234567890abcdef' } }}
      />,
    )
    expect(screen.getByText('0x1234567890abcdef')).toBeInTheDocument()
    expect(screen.getByText('연결된 지갑')).toBeInTheDocument()
  })

  it('renders an error state when company account data is unavailable', () => {
    renderView(<CompanyAccountView state={{ status: 'error', error: new Error('조회 실패') }} />)

    expect(screen.getByRole('alert')).toHaveTextContent('조회 실패')
  })
})
