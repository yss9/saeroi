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

/**
 * API DTO가 확정되기 전까지 화면 계층이 사용하는 입력 모델이다.
 * 실제 요청 DTO 변환은 회원가입 API를 연결하는 단계에서 api/ 아래에 둔다.
 */
export type ConsumerSignupSubmitValues = Omit<ConsumerSignupFormValues, 'passwordConfirm'>
