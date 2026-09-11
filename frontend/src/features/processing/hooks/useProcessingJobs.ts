import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processingJobApi, type ProcessingSignatureAction, type ResultDraftInput } from '../api/processingJobApi.ts'
import { signMessage } from '../../wallet/services/walletService.ts'

const keys = { all: ['processing-jobs'] as const, detail: (id: number) => ['processing-jobs', id] as const }

export function useProcessingJobs() {
  return useQuery({ queryKey: keys.all, queryFn: processingJobApi.list })
}

export function useProcessingJob(jobId: number) {
  const client = useQueryClient()
  const detail = useQuery({ queryKey: keys.detail(jobId), queryFn: () => processingJobApi.detail(jobId) })
  const refresh = async () => Promise.all([
    client.invalidateQueries({ queryKey: keys.detail(jobId) }),
    client.invalidateQueries({ queryKey: keys.all }),
  ])
  const ship = useMutation({ mutationFn: (input: { carrier?: string; trackingNumber?: string }) => processingJobApi.ship(jobId, input), onSuccess: refresh })
  const receive = useMutation({ mutationFn: () => processingJobApi.receive(jobId), onSuccess: refresh })
  const start = useMutation({ mutationFn: () => processingJobApi.start(jobId), onSuccess: refresh })
  const saveResult = useMutation({ mutationFn: (input: ResultDraftInput) => processingJobApi.saveResult(jobId, input), onSuccess: refresh })

  async function signed(action: ProcessingSignatureAction, reason?: string) {
    const challenge = await processingJobApi.challenge(jobId, action, reason)
    const signature = await signMessage(challenge.message)
    if (action === 'RESULT_SUBMIT') return processingJobApi.submitResult(jobId, challenge.challengeId, signature)
    if (action === 'APPROVE') return processingJobApi.approve(jobId, challenge.challengeId, signature)
    return processingJobApi.reject(jobId, challenge.challengeId, signature, reason ?? '')
  }
  const submit = useMutation({ mutationFn: () => signed('RESULT_SUBMIT'), onSuccess: refresh })
  const approve = useMutation({ mutationFn: () => signed('APPROVE'), onSuccess: refresh })
  const reject = useMutation({ mutationFn: (reason: string) => signed('REJECT', reason), onSuccess: refresh })
  return { detail, ship, receive, start, saveResult, submit, approve, reject }
}
