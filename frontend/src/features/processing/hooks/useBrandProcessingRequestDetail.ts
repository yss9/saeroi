import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingJobApi, type ProcessingJobDetail } from '../api/processingJobApi.ts'

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
  const jobsKey = processingKeys.list({ resource: 'jobs' })
  const jobs = useQuery({ queryKey: jobsKey, queryFn: processingJobApi.list })
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
  const ship = useMutation({
    mutationFn: (input: { carrier?: string; trackingNumber?: string }) => {
      if (!job) throw new Error('가공 작업을 찾을 수 없습니다.')
      return processingJobApi.ship(job.jobId, input)
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData<ProcessingJobDetail[]>(jobsKey, current => current?.map(item => item.jobId === updated.jobId ? updated : item))
      await queryClient.invalidateQueries({ queryKey: processingKeys.all })
    },
  })

  return { detail, quotes, selection, jobs, job, ship }
}
