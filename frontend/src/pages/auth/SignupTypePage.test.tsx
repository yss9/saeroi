import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { SignupTypePage } from './SignupTypePage.tsx'

afterEach(cleanup)

describe('SignupTypePage', () => {
  it('offers the personal and business signup paths', () => {
    render(
      <MemoryRouter>
        <SignupTypePage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '회원 유형을 선택해 주세요' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /개인회원으로 가입/ })).toHaveAttribute(
      'href',
      '/signup/consumer',
    )
    expect(screen.getByRole('link', { name: /사업자회원으로 가입/ })).toHaveAttribute(
      'href',
      '/signup/business',
    )
    expect(screen.queryByText(/Google|Apple|카카오/)).not.toBeInTheDocument()
  })
})
