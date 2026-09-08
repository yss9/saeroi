import { apiClient } from '../../../services/apiClient.ts'
import { isAppError } from '../../../utils/errors.ts'
import type { MaterialType, ProcessorCapability, ProcessorLeadTime, ProcessorProfile } from '../types/processorProfile.types.ts'

export interface ProcessorProfileInput { region: string }
export interface ProcessorCapabilitiesInput { capabilities: ProcessorCapability[] }
export interface ProcessorLeadTimesInput { leadTimes: ProcessorLeadTime[] }

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
  updateProfile: async (input: ProcessorProfileInput) => (await apiClient.put<ProcessorProfile>('/v1/processors/me/profile', input)).data,
  updateCapabilities: async (input: ProcessorCapabilitiesInput) => (await apiClient.put<ProcessorCapability[]>('/v1/processors/me/capabilities', input)).data,
  updateLeadTimes: async (input: ProcessorLeadTimesInput) => (await apiClient.put<ProcessorLeadTime[]>('/v1/processors/me/lead-times', input)).data,
}

export type { MaterialType }
