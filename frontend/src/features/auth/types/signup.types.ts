export interface ConsumerSignupFormValues {
  name: string
  email: string
  phone: string
  password: string
  passwordConfirm: string
  agreeTerms: boolean
  agreePrivacy: boolean
  agreeMarketing: boolean
}

export type ConsumerSignupSubmitValues = Omit<ConsumerSignupFormValues, 'passwordConfirm'>

export type ConsumerSignupRequest = Pick<
  ConsumerSignupSubmitValues,
  'name' | 'email' | 'phone' | 'password'
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
