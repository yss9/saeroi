import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { processingRequestApi, type ProcessingRequestListFilter } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import type { ProcessingRequestStatus } from '../types/processing.types.ts'

const PAGE_SIZE = 20

export function useBrandProcessingRequests() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<ProcessingRequestStatus | undefined>()
  const filters = useMemo<ProcessingRequestListFilter>(
    () => ({ page, size: PAGE_SIZE, ...(status ? { status } : {}) }),
    [page, status],
  )
  const query = useQuery({
    queryKey: processingKeys.list({ brandRequests: filters }),
    queryFn: ({ signal }) => processingRequestApi.list(filters, signal),
  })

  return {
    ...query,
    page,
    status,
    selectStatus(nextStatus?: ProcessingRequestStatus) {
      setStatus(nextStatus)
      setPage(0)
    },
    setPage,
  }
}
