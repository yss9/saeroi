export interface ConsumerSignupFormValues {
  name: string
  email: string
  phone: string
  /** 우편번호. 주소 검색이 채운다 — 직접 입력하지 않는다 */
  zipCode: string
  address: string
  /** 동·호수 등. 검색으로는 알 수 없어 사람이 적는다 */
  addressDetail: string
  password: string
  passwordConfirm: string
  agreeTerms: boolean
  agreePrivacy: boolean
  agreeMarketing: boolean
}

export type ConsumerSignupSubmitValues = Omit<ConsumerSignupFormValues, 'passwordConfirm'>

export type ConsumerSignupRequest = Pick<
  ConsumerSignupSubmitValues,
  'name' | 'email' | 'phone' | 'zipCode' | 'address' | 'addressDetail' | 'password'
>

export type BusinessRole = 'SUPPLIER' | 'BRAND' | 'PROCESSOR'

export interface BusinessSignupFormValues {
  role: BusinessRole
  companyName: string
  businessNumber: string
  representativeName: string
  companyPhone: string
  zipCode: string
  address: string
  addressDetail: string
  email: string
  password: string
  passwordConfirm: string
  agreeTerms: boolean
  agreePrivacy: boolean
  agreeMarketing: boolean
}

export type BusinessSignupSubmitValues = Omit<BusinessSignupFormValues, 'passwordConfirm'>

export type BusinessSignupRequest = Pick<
  BusinessSignupSubmitValues,
  | 'role'
  | 'companyName'
  | 'businessNumber'
  | 'representativeName'
  | 'companyPhone'
  | 'zipCode'
  | 'address'
  | 'addressDetail'
  | 'email'
  | 'password'
>
