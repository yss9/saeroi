import { apiClient } from '../../../services/apiClient.ts'
import { isAppError } from '../../../utils/errors.ts'
import type { MaterialType, ProcessorCapability, ProcessorLeadTime, ProcessorProfile } from '../types/processorProfile.types.ts'

export interface ProcessorProfileInput { region: string }
export interface ProcessorCapabilitiesInput { capabilities: ProcessorCapability[] }
export interface ProcessorLeadTimesInput { leadTimes: ProcessorLeadTime[] }
export interface ProcessorProfileImage { imageUrl: string }

export const processorProfileApi = {
  async getProfile(): Promise<ProcessorProfile | null> {
    try {
      return (await apiClient.get<ProcessorProfile>('/v1/processors/me/profile')).data
    } catch (error) {
      if (isAppError(error) && error.code === 'PROCESSOR_PROFILE_NOT_FOUND') return null
      throw error
    }
  },
  getCapabilities: async () => (await apiClient.get<ProcessorCapability[]>('/v1/processors/me/capabilities')).data,
  getLeadTimes: async () => (await apiClient.get<ProcessorLeadTime[]>('/v1/processors/me/lead-times')).data,
  async getImage(): Promise<Blob | null> {
    try {
      return (await apiClient.get<Blob>('/v1/processors/me/profile/image', { responseType: 'blob' })).data
    } catch (error) {
      // blob 응답을 요청하면 일부 브라우저에서 404 본문도 Blob이 되어 오류 코드가
      // 역직렬화되지 않는다. 이 전용 조회 경로의 404는 모두 "미등록"으로 취급한다.
      if (isAppError(error) && (error.code === 'PROCESSOR_PROFILE_IMAGE_NOT_FOUND' || error.status === 404)) return null
      throw error
    }
  },
  uploadImage: async (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return (await apiClient.post<ProcessorProfileImage>('/v1/processors/me/profile/image', body, {
      headers: { 'Content-Type': undefined },
    })).data
  },
  updateProfile: async (input: ProcessorProfileInput) => (await apiClient.put<ProcessorProfile>('/v1/processors/me/profile', input)).data,
  updateCapabilities: async (input: ProcessorCapabilitiesInput) => (await apiClient.put<ProcessorCapability[]>('/v1/processors/me/capabilities', input)).data,
  updateLeadTimes: async (input: ProcessorLeadTimesInput) => (await apiClient.put<ProcessorLeadTime[]>('/v1/processors/me/lead-times', input)).data,
}

export type { MaterialType }
