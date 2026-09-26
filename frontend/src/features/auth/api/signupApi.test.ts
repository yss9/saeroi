import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import type { UserProfile } from '../types/auth.types.ts'
import type {
  BusinessSignupSubmitValues,
  ConsumerSignupSubmitValues,
} from '../types/signup.types.ts'
import { signupBusiness, signupConsumer } from './signupApi.ts'

const RESPONSE: UserProfile = {
  id: 1,
  email: 'member@saeroi.test',
  name: '홍길동',
  phone: '010-1234-5678',
  role: 'CONSUMER',
  status: 'ACTIVE',
  createdAt: '2026-09-08T12:00:00',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('signup API', () => {
  it('개인회원 필수 정보만 Backend 회원가입 경로로 전송한다', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValue({ data: RESPONSE } as AxiosResponse<UserProfile>)
    const values: ConsumerSignupSubmitValues = {
      name: ' 홍길동 ',
      email: ' member@saeroi.test ',
      phone: ' 010-1234-5678 ',
      // 주소는 제품을 받을 곳이다. 검색으로 채워지지만 상세 주소는 사람이 적어 공백이 붙는다.
      zipCode: ' 06236 ',
      address: ' 서울특별시 강남구 테헤란로 212 ',
      addressDetail: ' 3층 ',
      password: 'Password!1',
      agreeTerms: true,
      agreePrivacy: true,
      agreeMarketing: true,
    }

    await expect(signupConsumer(values)).resolves.toEqual(RESPONSE)

    // 동의 여부는 보내지 않는다 — 서버가 받지 않는 값이다.
    expect(post).toHaveBeenCalledWith('/v1/auth/signup/consumer', {
      name: '홍길동',
      email: 'member@saeroi.test',
      phone: '010-1234-5678',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '3층',
      password: 'Password!1',
    })
  })

  it('사업자 계정과 업체 정보를 Backend 회원가입 경로로 전송한다', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValue({ data: { ...RESPONSE, role: 'BRAND' } } as AxiosResponse<UserProfile>)
    const values: BusinessSignupSubmitValues = {
      role: 'BRAND',
      companyName: '새로이 브랜드',
      businessNumber: '123-45-67890',
      representativeName: '김새로',
      companyPhone: '02-1234-5678',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '2층',
      email: 'brand@saeroi.test',
      password: 'Password!1',
      agreeTerms: true,
      agreePrivacy: true,
      agreeMarketing: false,
    }

    await signupBusiness(values)

    expect(post).toHaveBeenCalledWith('/v1/auth/signup/business', {
      role: 'BRAND',
      companyName: '새로이 브랜드',
      businessNumber: '123-45-67890',
      representativeName: '김새로',
      companyPhone: '02-1234-5678',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '2층',
      email: 'brand@saeroi.test',
      password: 'Password!1',
    })
  })
})
