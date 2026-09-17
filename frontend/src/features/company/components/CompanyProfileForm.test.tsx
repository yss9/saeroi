// 업체 정보 수정 폼.
//
// 틀리면 안 되는 지점:
//
//   1) **지금 값으로 채워져 있어야 한다.** 서버가 전부 필수로 받으므로(PUT 은 부분
//      수정이 아니다) 빈 폼을 주면 한 칸만 고치려던 사람이 나머지를 지운 채 저장한다.
//   2) **고칠 수 없는 값은 칸으로 만들지 않는다.** 업체명·사업자등록번호·이메일은
//      서버가 받지 않는다. 칸이 있으면 고쳐지는 줄 알고 적었다가 조용히 버려진다.
//   3) **검증은 서버 규칙과 같아야 한다.** 느슨하면 저장 뒤에야 400 을 보고, 빡빡하면
//      서버가 받아 줄 값을 화면이 거절한다.
//   4) **주소는 검색으로만 채운다.** 우편번호와 주소가 서로 다른 곳을 가리키는 조합은
//      손으로 적을 때 쉽게 만들어지고, 화면은 그것을 알 수 없다.

import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CompanyProfile } from '../api/getCompanyProfile.ts'
import { CompanyProfileForm } from './CompanyProfileForm.tsx'

// 우편번호 검색은 카카오 CDN 스크립트를 주입하고 팝업을 띄운다. 테스트 환경에서는 뜨지 않는다.
const postcode = vi.hoisted(() => ({ openKakaoPostcode: vi.fn() }))
vi.mock('../../../utils/kakaoPostcode.ts', () => postcode)

const PROFILE: CompanyProfile = {
  userId: 7,
  companyName: '수거업체 A',
  businessNumber: '123-45-67890',
  representativeName: '김대표',
  companyPhone: '02-1234-5678',
  zipCode: '04781',
  address: '서울 성동구 성수일로 89',
  addressDetail: '3층',
  introduction: '폐데님을 주로 다룹니다.',
  logoUrl: null,
  role: 'SUPPLIER',
  status: 'ACTIVE',
}

function renderForm(overrides: Partial<CompanyProfile> = {}) {
  const onSubmit = vi.fn()
  const onCancel = vi.fn()
  render(
    <CompanyProfileForm
      profile={{ ...PROFILE, ...overrides }}
      onSubmit={onSubmit}
      onCancel={onCancel}
    />,
  )
  return { onSubmit, onCancel }
}

beforeEach(() => {
  postcode.openKakaoPostcode.mockReset()
})

afterEach(cleanup)

describe('지금 값으로 채운다', () => {
  it('고칠 수 있는 칸이 현재 값으로 차 있다', () => {
    renderForm()

    // PUT 은 부분 수정이 아니다. 빈 폼이면 한 칸만 고쳐도 나머지가 지워진다.
    expect(screen.getByLabelText('대표자명')).toHaveValue('김대표')
    expect(screen.getByLabelText('업체 연락처')).toHaveValue('02-1234-5678')
    expect(screen.getByLabelText('우편번호')).toHaveValue('04781')
    expect(screen.getByLabelText('주소')).toHaveValue('서울 성동구 성수일로 89')
    expect(screen.getByLabelText('상세 주소')).toHaveValue('3층')
    expect(screen.getByLabelText('업체 소개')).toHaveValue('폐데님을 주로 다룹니다.')
  })

  it('비어 있던 선택 값은 빈 칸이다 — null 이 글자로 새지 않는다', () => {
    renderForm({ addressDetail: null, introduction: null })

    expect(screen.getByLabelText('상세 주소')).toHaveValue('')
    expect(screen.getByLabelText('업체 소개')).toHaveValue('')
  })
})

describe('고칠 수 없는 값은 칸으로 만들지 않는다', () => {
  it('업체명·사업자등록번호·이메일 칸이 없다', () => {
    // 서버가 받지 않는 값이다. 칸이 있으면 적었다가 조용히 버려진다.
    renderForm()

    expect(screen.queryByLabelText('업체명')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('사업자등록번호')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('이메일')).not.toBeInTheDocument()
  })
})

describe('검증', () => {
  it('업체 연락처 형식이 틀리면 보내지 않는다', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderForm()

    const phone = screen.getByLabelText('업체 연락처')
    await user.clear(phone)
    await user.type(phone, '010')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText(/업체 연락처 형식이 올바르지 않습니다/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('대표자명이 한 글자면 보내지 않는다', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderForm()

    const name = screen.getByLabelText('대표자명')
    await user.clear(name)
    await user.type(name, '김')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('대표자명은 2자 이상 입력해 주세요.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('고친 값을 그대로 보낸다', async () => {
    const user = userEvent.setup()
    const { onSubmit } = renderForm()

    const name = screen.getByLabelText('대표자명')
    await user.clear(name)
    await user.type(name, '박대표')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit.mock.calls[0][0]).toEqual({
      representativeName: '박대표',
      companyPhone: '02-1234-5678',
      zipCode: '04781',
      address: '서울 성동구 성수일로 89',
      addressDetail: '3층',
      introduction: '폐데님을 주로 다룹니다.',
    })
  })
})

describe('주소', () => {
  it('우편번호와 주소는 손으로 못 고친다', () => {
    renderForm()

    expect(screen.getByLabelText('우편번호')).toHaveAttribute('readonly')
    expect(screen.getByLabelText('주소')).toHaveAttribute('readonly')
    // 비활성이 아니라 readOnly 다 — 비활성 입력은 폼 제출에서 값이 빠진다.
    expect(screen.getByLabelText('우편번호')).toBeEnabled()
  })

  it('검색 결과가 우편번호와 주소를 함께 바꾼다', async () => {
    const user = userEvent.setup()
    postcode.openKakaoPostcode.mockImplementation(
      async (onComplete: (value: { zonecode: string; address: string }) => void) => {
        onComplete({ zonecode: '06236', address: '서울 강남구 테헤란로 152' })
      },
    )
    const { onSubmit } = renderForm()

    await user.click(screen.getByRole('button', { name: /주소 검색/ }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    // 둘이 따로 바뀌면 우편번호와 주소가 다른 곳을 가리킨다.
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      zipCode: '06236',
      address: '서울 강남구 테헤란로 152',
    })
  })
})
