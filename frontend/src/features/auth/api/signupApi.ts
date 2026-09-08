import { apiClient } from '../../../services/apiClient.ts'
import type { UserProfile } from '../types/auth.types.ts'
import type {
  BusinessSignupRequest,
  BusinessSignupSubmitValues,
  ConsumerSignupRequest,
  ConsumerSignupSubmitValues,
} from '../types/signup.types.ts'

export async function signupConsumer(values: ConsumerSignupSubmitValues): Promise<UserProfile> {
  const payload: ConsumerSignupRequest = {
    name: values.name.trim(),
    email: values.email.trim(),
    phone: values.phone.trim(),
    password: values.password,
  }
  const { data } = await apiClient.post<UserProfile>('/v1/auth/signup/consumer', payload)
  return data
}

export async function signupBusiness(values: BusinessSignupSubmitValues): Promise<UserProfile> {
  const payload: BusinessSignupRequest = {
    role: values.role,
    companyName: values.companyName.trim(),
    businessNumber: values.businessNumber.trim(),
    representativeName: values.representativeName.trim(),
    companyPhone: values.companyPhone.trim(),
    zipCode: values.zipCode.trim(),
    address: values.address.trim(),
    addressDetail: values.addressDetail.trim(),
    email: values.email.trim(),
    password: values.password,
  }
  const { data } = await apiClient.post<UserProfile>('/v1/auth/signup/business', payload)
  return data
}
