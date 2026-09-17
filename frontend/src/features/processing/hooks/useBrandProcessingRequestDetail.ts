import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingJobApi } from '../api/processingJobApi.ts'

export function useBrandProcessingRequestDetail(requestId: number) {
  const queryClient = useQueryClient()
  const detail = useQuery({
    queryKey: processingKeys.detail(requestId),
    queryFn: ({ signal }) => processingRequestApi.detail(requestId, signal),
  })
  const quotes = useQuery({
    queryKey: processingKeys.list({ requestId, resource: 'quotes' }),
    queryFn: ({ signal }) => processingRequestApi.quotes(requestId, signal),
  })
  const jobs = useQuery({ queryKey: processingKeys.list({ resource: 'jobs' }), queryFn: processingJobApi.list })
  const job = jobs.data?.find(item => item.requestId === requestId)
  const selection = useMutation({
    mutationFn: (quoteId: number) => processingRequestApi.selectQuote(quoteId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: processingKeys.detail(requestId) }),
        queryClient.invalidateQueries({ queryKey: processingKeys.lists() }),
      ])
    },
  })
  return { detail, quotes, selection, jobs, job }
}
