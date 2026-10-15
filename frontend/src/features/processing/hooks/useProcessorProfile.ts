import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processorProfileApi, type ProcessorCapabilitiesInput, type ProcessorProfileInput } from '../api/processorProfileApi.ts'

const keys = {
  profile: ['processor', 'profile'] as const,
  capabilities: ['processor', 'capabilities'] as const,
  image: ['processor', 'profile-image'] as const,
}

export function useProcessorProfile() {
  const client = useQueryClient()
  const profile = useQuery({ queryKey: keys.profile, queryFn: processorProfileApi.getProfile, retry: false })
  const capabilities = useQuery({ queryKey: keys.capabilities, queryFn: processorProfileApi.getCapabilities })
  const image = useQuery({ queryKey: keys.image, queryFn: processorProfileApi.getImage, retry: false })
  const uploadImage = useMutation({
    mutationFn: processorProfileApi.uploadImage,
    onSuccess: async () => client.invalidateQueries({ queryKey: keys.image }),
  })
  const saveProfile = useMutation({
    mutationFn: (input: ProcessorProfileInput) => processorProfileApi.updateProfile(input),
    onSuccess: async () => client.invalidateQueries({ queryKey: keys.profile }),
  })
  // 소요일 표는 없앴다 — 소요일은 하루 처리량에서 계산된다.
  const saveConditions = useMutation({
    mutationFn: (input: ProcessorCapabilitiesInput) => processorProfileApi.updateCapabilities(input),
    onSuccess: async () => client.invalidateQueries({ queryKey: keys.capabilities }),
  })
  return { profile, capabilities, image, uploadImage, saveProfile, saveConditions }
}
