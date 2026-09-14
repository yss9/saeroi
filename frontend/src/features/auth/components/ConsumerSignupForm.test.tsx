import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerSignupForm } from './ConsumerSignupForm.tsx'

// 주소 검색은 카카오 스크립트를 불러와 팝업을 띄운다. 여기서 볼 것은 "고른 주소가 폼에
// 들어오는가" 이지 그 팝업이 아니다.
const openKakaoPostcode = vi.hoisted(() =>
  vi.fn((onComplete: (selection: { zonecode: string; address: string }) => void) => {
    onComplete({ zonecode: '06236', address: '서울특별시 강남구 테헤란로 212' })
    return Promise.resolve()
  }),
)
vi.mock('../../../utils/kakaoPostcode.ts', () => ({ openKakaoPostcode }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ConsumerSignupForm', () => {
  it('validates required fields without submitting incomplete values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<ConsumerSignupForm onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('이메일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('휴대전화 번호를 입력해 주세요.')).toBeInTheDocument()
    // 주소가 없으면 제품을 보낼 곳이 없다. 두 칸 모두 읽기 전용이라 안내는 "검색해 달라" 여야
    // 한다 — "입력해 주세요" 라고 하면 칠 수 없는 칸을 치라는 말이 된다.
    expect(screen.getByText('주소 검색으로 우편번호를 채워 주세요.')).toBeInTheDocument()
    expect(screen.getByText('주소를 검색해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('이용약관에 동의해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('개인정보 수집 및 이용에 동의해 주세요.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('passes validated values through the API integration boundary', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<ConsumerSignupForm onSubmit={onSubmit} />)

    await user.type(screen.getByLabelText('이름'), '김새로')
    await user.type(screen.getByLabelText('휴대전화 번호'), '01012345678')
    expect(screen.getByLabelText('휴대전화 번호')).toHaveValue('010-1234-5678')
    await user.type(screen.getByLabelText('이메일'), 'consumer@saeroi.test')
    await user.click(screen.getByRole('button', { name: '주소 검색' }))
    expect(await screen.findByLabelText('우편번호')).toHaveValue('06236')
    expect(screen.getByLabelText('주소')).toHaveValue('서울특별시 강남구 테헤란로 212')
    // 검색이 못 채우는 칸으로 바로 보낸다. 사용자가 다음 칸을 찾아 내려가지 않아도 된다.
    expect(screen.getByLabelText('상세 주소')).toHaveFocus()
    await user.type(screen.getByLabelText('상세 주소'), '101동 1004호')
    await user.type(screen.getByLabelText('비밀번호'), 'Saeroi!123')
    await user.type(screen.getByLabelText('비밀번호 확인'), 'Saeroi!123')
    await user.click(screen.getByLabelText('[필수] 이용약관 동의'))
    await user.click(screen.getByLabelText('[필수] 개인정보 수집 및 이용 동의'))
    await user.click(screen.getByLabelText('[선택] 마케팅 및 이벤트 소식 수신 동의'))
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        name: '김새로',
        email: 'consumer@saeroi.test',
        phone: '010-1234-5678',
        zipCode: '06236',
        address: '서울특별시 강남구 테헤란로 212',
        addressDetail: '101동 1004호',
        password: 'Saeroi!123',
        agreeTerms: true,
        agreePrivacy: true,
        agreeMarketing: true,
      })
    })
  })

  // 두 칸이 읽기 전용이라 검색이 실패하면 사용자가 손으로 메울 방법이 없다. 조용히
  // 넘기면 가입이 막힌 것처럼 보인다.
  it('주소 검색이 실패하면 이유를 알린다', async () => {
    openKakaoPostcode.mockRejectedValueOnce(new Error('script blocked'))
    const user = userEvent.setup()
    render(<ConsumerSignupForm onSubmit={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('주소 검색을 불러오지 못했어요')
    expect(screen.getByLabelText('우편번호')).toHaveValue('')
  })

  it('can reveal and hide the password without changing its value', async () => {
    const user = userEvent.setup()
    render(<ConsumerSignupForm onSubmit={vi.fn()} />)
    const password = screen.getByLabelText('비밀번호')

    await user.type(password, 'Saeroi!123')
    await user.click(screen.getByRole('button', { name: '비밀번호 보기' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('Saeroi!123')

    await user.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
    expect(password).toHaveAttribute('type', 'password')
  })
})
