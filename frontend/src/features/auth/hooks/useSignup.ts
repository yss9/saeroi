import { useMutation } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { signupBusiness, signupConsumer } from '../api/signupApi.ts'
import type { UserProfile } from '../types/auth.types.ts'
import type {
  BusinessSignupSubmitValues,
  ConsumerSignupSubmitValues,
} from '../types/signup.types.ts'

export function useConsumerSignup() {
  return useMutation<UserProfile, AppError, ConsumerSignupSubmitValues>({
    mutationFn: signupConsumer,
  })
}

export function useBusinessSignup() {
  return useMutation<UserProfile, AppError, BusinessSignupSubmitValues>({
    mutationFn: signupBusiness,
  })
}
