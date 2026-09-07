import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { ConsumerSignupPage } from './ConsumerSignupPage.tsx'

afterEach(cleanup)

describe('ConsumerSignupPage', () => {
  it('renders a personal signup form without wallet or profile image fields', () => {
    render(
      <MemoryRouter>
        <ConsumerSignupPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '개인회원 가입' })).toBeInTheDocument()
    expect(screen.getByLabelText('이름')).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toBeInTheDocument()
    expect(screen.queryByText(/MetaMask|지갑 연결/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/프로필 이미지/)).not.toBeInTheDocument()
  })
})
