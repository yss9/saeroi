import { cleanup, render, screen } from '@testing-library/react'
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

describe('CompanyAccountView', () => {
  it('renders company identity and registered business information', () => {
    render(<CompanyAccountView state={{ status: 'ready', data: company }} />)

    expect(screen.getByRole('heading', { name: '회사 정보', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '새로이 브랜드' })).toBeInTheDocument()
    expect(screen.getAllByText('브랜드').length).toBeGreaterThan(0)
    expect(screen.getByText('123-45-67890')).toBeInTheDocument()
    expect(screen.getByText('김새로')).toBeInTheDocument()
    expect(screen.getByText('활성 계정')).toBeInTheDocument()
  })

  it('shows wallet status without exposing a connection action yet', () => {
    const { rerender } = render(<CompanyAccountView state={{ status: 'ready', data: company }} />)

    expect(screen.getByText('아직 연결된 지갑이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /지갑 연결/ })).not.toBeInTheDocument()

    rerender(
      <CompanyAccountView
        state={{ status: 'ready', data: { ...company, walletAddress: '0x1234567890abcdef' } }}
      />,
    )
    expect(screen.getByText('0x1234567890abcdef')).toBeInTheDocument()
    expect(screen.getByText('연결된 지갑')).toBeInTheDocument()
  })

  it('renders an error state when company account data is unavailable', () => {
    render(<CompanyAccountView state={{ status: 'error', error: new Error('조회 실패') }} />)

    expect(screen.getByRole('alert')).toHaveTextContent('조회 실패')
  })
})
