import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { BusinessSignupPage } from './BusinessSignupPage.tsx'

afterEach(cleanup)

describe('BusinessSignupPage', () => {
  it('uses the company as the business account identity', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <BusinessSignupPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: '사업자회원 가입', level: 1 })).toBeInTheDocument()
    expect(screen.getByLabelText('기업명')).toBeInTheDocument()
    expect(screen.getByLabelText('대표자명')).toBeInTheDocument()
    expect(screen.queryByLabelText(/담당자/)).not.toBeInTheDocument()
    expect(screen.queryByText(/MetaMask|지갑 연결/)).not.toBeInTheDocument()
  })
})
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
