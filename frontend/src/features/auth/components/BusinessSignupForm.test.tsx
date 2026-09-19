import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { BusinessSignupForm } from './BusinessSignupForm.tsx'

const openKakaoPostcode = vi.hoisted(() => vi.fn())

vi.mock('../../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})
describe('BusinessSignupForm', () => {
  it('requires a business role and company information', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<BusinessSignupForm onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: '사업자회원 가입하기' }))

    expect(await screen.findByText('기업 유형을 선택해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('기업명을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('사업자 등록번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('우편번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('기업 주소를 입력해 주세요.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('passes company identity and the selected role through the API boundary', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<BusinessSignupForm onSubmit={onSubmit} />)

    openKakaoPostcode.mockImplementation(async (onComplete) => {
      onComplete({ zonecode: '06236', address: '서울특별시 강남구 테헤란로 212' })
    })

    await user.click(screen.getByRole('radio', { name: /브랜드/ }))
    await user.type(screen.getByLabelText('기업명'), '새로이 브랜드')
    await user.type(screen.getByLabelText('대표자명'), '김새로')
    await user.type(screen.getByLabelText('사업자 등록번호'), '1234567890')
    expect(screen.getByLabelText('사업자 등록번호')).toHaveValue('123-45-67890')
    await user.type(screen.getByLabelText('기업 연락처'), '0212345678')
    expect(screen.getByLabelText('기업 연락처')).toHaveValue('02-1234-5678')
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    expect(await screen.findByLabelText('우편번호')).toHaveValue('06236')
    expect(screen.getByLabelText('기업 주소')).toHaveValue('서울특별시 강남구 테헤란로 212')
    expect(screen.getByLabelText('상세 주소')).toHaveFocus()
    await user.type(screen.getByLabelText('상세 주소'), '2층')
    await user.type(screen.getByLabelText('이메일'), 'brand@saeroi.test')
    await user.type(screen.getByLabelText('비밀번호'), 'Saeroi!123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'Saeroi!123')
    await user.click(screen.getByLabelText('[필수] 이용약관 동의'))
    await user.click(screen.getByLabelText('[필수] 개인정보 수집 및 이용 동의'))
    await user.click(screen.getByRole('button', { name: '사업자회원 가입하기' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        role: 'BRAND',
        companyName: '새로이 브랜드',
        businessNumber: '123-45-67890',
        representativeName: '김새로',
        companyPhone: '02-1234-5678',
        zipCode: '06236',
        address: '서울특별시 강남구 테헤란로 212',
        addressDetail: '2층',
        email: 'brand@saeroi.test',
        password: 'Saeroi!123',
        agreeTerms: true,
        agreePrivacy: true,
        agreeMarketing: false,
      })
    })
  })
})
