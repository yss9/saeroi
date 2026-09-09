import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'

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
  const selection = useMutation({
    mutationFn: (quoteId: number) => processingRequestApi.selectQuote(quoteId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: processingKeys.detail(requestId) }),
        queryClient.invalidateQueries({ queryKey: processingKeys.lists() }),
      ])
    },
  })

  return { detail, quotes, selection }
}
