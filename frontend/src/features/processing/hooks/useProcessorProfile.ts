import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processorProfileApi, type ProcessorCapabilitiesInput, type ProcessorLeadTimesInput, type ProcessorProfileInput } from '../api/processorProfileApi.ts'

const keys = {
  profile: ['processor', 'profile'] as const,
  capabilities: ['processor', 'capabilities'] as const,
  leadTimes: ['processor', 'lead-times'] as const,
}

export function useProcessorProfile() {
  const client = useQueryClient()
  const profile = useQuery({ queryKey: keys.profile, queryFn: processorProfileApi.getProfile, retry: false })
  const capabilities = useQuery({ queryKey: keys.capabilities, queryFn: processorProfileApi.getCapabilities })
  const leadTimes = useQuery({ queryKey: keys.leadTimes, queryFn: processorProfileApi.getLeadTimes })
  const save = useMutation({
    mutationFn: async (input: { profile: ProcessorProfileInput; capabilities: ProcessorCapabilitiesInput; leadTimes: ProcessorLeadTimesInput }) => {
      await processorProfileApi.updateProfile(input.profile)
      await processorProfileApi.updateCapabilities(input.capabilities)
      await processorProfileApi.updateLeadTimes(input.leadTimes)
    },
    onSuccess: async () => {
      await Promise.all([client.invalidateQueries({ queryKey: keys.profile }), client.invalidateQueries({ queryKey: keys.capabilities }), client.invalidateQueries({ queryKey: keys.leadTimes })])
    },
  })
  return { profile, capabilities, leadTimes, save }
}
