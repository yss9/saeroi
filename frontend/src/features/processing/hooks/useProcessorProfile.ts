import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processorProfileApi, type ProcessorCapabilitiesInput, type ProcessorLeadTimesInput, type ProcessorProfileInput } from '../api/processorProfileApi.ts'

const keys = {
  profile: ['processor', 'profile'] as const,
  capabilities: ['processor', 'capabilities'] as const,
  leadTimes: ['processor', 'lead-times'] as const,
  image: ['processor', 'profile-image'] as const,
}

export function useProcessorProfile() {
  const client = useQueryClient()
  const profile = useQuery({ queryKey: keys.profile, queryFn: processorProfileApi.getProfile, retry: false })
  const capabilities = useQuery({ queryKey: keys.capabilities, queryFn: processorProfileApi.getCapabilities })
  const leadTimes = useQuery({ queryKey: keys.leadTimes, queryFn: processorProfileApi.getLeadTimes })
  const image = useQuery({ queryKey: keys.image, queryFn: processorProfileApi.getImage, retry: false })
  const uploadImage = useMutation({
    mutationFn: processorProfileApi.uploadImage,
    onSuccess: async () => client.invalidateQueries({ queryKey: keys.image }),
  })
  const saveProfile = useMutation({
    mutationFn: (input: ProcessorProfileInput) => processorProfileApi.updateProfile(input),
    onSuccess: async () => client.invalidateQueries({ queryKey: keys.profile }),
  })
  const saveConditions = useMutation({
    mutationFn: async (input: { capabilities: ProcessorCapabilitiesInput; leadTimes: ProcessorLeadTimesInput }) => {
      await processorProfileApi.updateCapabilities(input.capabilities)
      await processorProfileApi.updateLeadTimes(input.leadTimes)
    },
    onSuccess: async () => {
      await Promise.all([client.invalidateQueries({ queryKey: keys.capabilities }), client.invalidateQueries({ queryKey: keys.leadTimes })])
    },
  })
  return { profile, capabilities, leadTimes, image, uploadImage, saveProfile, saveConditions }
}
