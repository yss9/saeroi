import { ArrowRight, CaretDown, CaretLeft, CaretRight, Clock, Factory, Package } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import type { ProcessingJobDetail } from '../api/processingJobApi.ts'
import type { ProcessingRequestListItem } from '../api/processingRequestApi.ts'
import { useProcessingJobs } from '../hooks/useProcessingJobs.ts'
import { useProcessorRequests } from '../hooks/useProcessorRequests.ts'
import { PROCESS_TYPE_LABEL, type ProcessingJobStatus, type ProcessingRequestStatus } from '../types/processing.types.ts'
import { ProcessorQuoteForm } from './ProcessorQuoteForm.tsx'
import { parseServerTime } from '../utils/processingTime.ts'
import './processor-work-list.css'

type WorkView = 'all' | 'requests' | 'jobs'
type WorkEntry =
  | { kind: 'request'; createdAt: string; request: ProcessingRequestListItem }
  | { kind: 'job'; createdAt: string; job: ProcessingJobDetail; request?: ProcessingRequestListItem }

const filters: Array<{ value: WorkView; label: string }> = [
  { value: 'all', label: '전체' },
  { value: 'requests', label: '견적 요청' },
  { value: 'jobs', label: '가공 작업' },
]
const requestStatusLabel: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기', SELECTED: '견적 수락', CANCELED: '취소', REJECTED: '거절', EXPIRED: '만료',
}
const jobStatusLabel: Record<ProcessingJobStatus, string> = {
  CREATED: '결제 대기', PAID: '발송 대기', SHIPPED: '수령 대기', RECEIVED: '가공 시작 대기',
  PROCESSING: '가공 중', RESULT_SUBMITTED: '검토 대기', RETURN_SHIPPED: '결과물 반환 중', CONFIRMED: '완료', REJECTED: '보완 요청', EXPIRED: '만료', CANCELED: '반환 종료',
}
const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const formatDate = (value: string) => parseServerTime(value).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '목록을 불러오지 못했어요.'

export function ProcessorWorkList() {
  const requests = useProcessorRequests()
  const jobs = useProcessingJobs()
  const [view, setView] = useState<WorkView>('all')
  const [expandedRequestId, setExpandedRequestId] = useState<number | null>(null)
  const requestItems = useMemo(() => requests.data?.items ?? [], [requests.data?.items])
  const requestById = useMemo(() => new Map(requestItems.map(request => [request.requestId, request])), [requestItems])
  const entries = useMemo<WorkEntry[]>(() => {
    const result: WorkEntry[] = []
    if (view !== 'jobs') result.push(...requestItems.map(request => ({ kind: 'request' as const, createdAt: request.createdAt, request })))
    if (view !== 'requests') result.push(...(jobs.data ?? []).map(job => ({ kind: 'job' as const, createdAt: job.createdAt, job, request: requestById.get(job.requestId) })))
    return result.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
  }, [jobs.data, requestById, requestItems, view])
  const requestPageCount = requests.data ? Math.max(1, Math.ceil(requests.data.total / requests.data.size)) : 1
  const isPending = (view !== 'jobs' && requests.isPending) || (view !== 'requests' && jobs.isPending)
  const errors = [view !== 'jobs' ? requests.error : null, view !== 'requests' ? jobs.error : null].filter(Boolean)

  return <section className="pw-shell">
    <header className="pw-heading">
      <h1 className="ui-visually-hidden">가공 업무</h1>
      <Factory size={30} weight="duotone" />
    </header>

    <nav className="pw-filters" aria-label="가공 업무 필터">
      {filters.map(filter => <button
        type="button"
        className={view === filter.value ? 'is-active' : ''}
        aria-pressed={view === filter.value}
        onClick={() => setView(filter.value)}
        key={filter.value}
      >{filter.label}</button>)}
    </nav>

    {errors.length > 0 && <div className="pw-error" role="alert">
      <span>{errorMessage(errors[0])}</span>
      <button type="button" onClick={() => { void requests.refetch(); void jobs.refetch() }}>다시 시도</button>
    </div>}

    {isPending ? <div className="pw-state" role="status">가공 업무를 불러오는 중이에요…</div>
      : entries.length === 0 ? <div className="pw-state"><Package size={36} /><strong>표시할 가공 업무가 없어요</strong></div>
        : <div className="pw-list" role="table" aria-label="가공 업무 목록">
          <div className="pw-row pw-row--head" role="row">
            <span>구분 / 번호</span><span>업체·소재 / 공정</span><span>수량·비용</span><span>접수일</span><span>상태</span>
          </div>
          {entries.map(entry => entry.kind === 'request'
            ? <RequestRow
                request={entry.request}
                expanded={expandedRequestId === entry.request.requestId}
                onToggle={() => setExpandedRequestId(current => current === entry.request.requestId ? null : entry.request.requestId)}
                key={`request-${entry.request.requestId}`}
              />
            : <JobRow job={entry.job} request={entry.request} key={`job-${entry.job.jobId}`} />)}
        </div>}

    {view !== 'jobs' && requestPageCount > 1 && <nav className="pw-pagination" aria-label="견적 요청 페이지">
      <button type="button" aria-label="이전 페이지" disabled={requests.page === 0} onClick={() => requests.setPage(requests.page - 1)}><CaretLeft /></button>
      <span><b>{requests.page + 1}</b> / {requestPageCount}</span>
      <button type="button" aria-label="다음 페이지" disabled={requests.page + 1 >= requestPageCount} onClick={() => requests.setPage(requests.page + 1)}><CaretRight /></button>
    </nav>}
  </section>
}

function RequestRow({ request, expanded, onToggle }: { request: ProcessingRequestListItem; expanded: boolean; onToggle: () => void }) {
  return <div className="pw-entry">
    <button type="button" className="pw-row pw-row--item" aria-label={`${request.requestCode} 요청 상세 보기`} aria-expanded={expanded} onClick={onToggle}>
      <span className="pw-identity"><b className="pw-kind is-request">견적 요청</b><strong>{request.requestCode}</strong></span>
      <span className="pw-subject"><strong>{request.brandName} · {request.materialName}</strong><small>{request.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')}</small></span>
      <span className="pw-amount"><strong>{grams(request.quantity)}</strong><small>견적 작성 전</small></span>
      <span className="pw-date"><Clock />{formatDate(request.createdAt)}</span>
      <span className="pw-action"><b className={`pw-status is-${request.status.toLowerCase()}`}>{requestStatusLabel[request.status]}</b><CaretDown className={expanded ? 'is-open' : ''} /></span>
    </button>
    {expanded && <div className="pw-detail">
      <div><span>소재 코드</span><strong>{request.materialCode}</strong></div>
      <div><span>소재 유형</span><strong>{request.materialType}</strong></div>
      <div><span>요청 수량</span><strong>{grams(request.quantity)}</strong></div>
      <div><span>만료 일시</span><strong>{formatDate(request.expiresAt)}</strong></div>
      {request.status === 'QUOTING'
        ? <ProcessorQuoteForm requestId={request.requestId} requestCode={request.requestCode} />
        : <p>현재 상태에서는 견적을 작성할 수 없습니다.</p>}
    </div>}
  </div>
}

function JobRow({ job, request }: { job: ProcessingJobDetail; request?: ProcessingRequestListItem }) {
  return <Link className="pw-row pw-row--item" to={`/processing/jobs/${job.jobId}`} aria-label={`${job.jobCode} 작업 상세 및 처리`}>
    <span className="pw-identity"><b className="pw-kind is-job">가공 작업</b><strong>{job.jobCode}</strong></span>
    <span className="pw-subject"><strong>{request ? `${request.brandName} · ${request.materialName}` : `투입 소재 #${job.inputMaterialId}`}</strong><small>{request?.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ') ?? '상세에서 작업 조건 확인'}</small></span>
    <span className="pw-amount"><strong>{grams(job.inputQuantity)}</strong><small>{job.fee.toLocaleString('ko-KR')}원</small></span>
    <span className="pw-date"><Clock />{formatDate(job.createdAt)}</span>
    <span className="pw-action"><b className={`pw-status is-${job.status.toLowerCase()}`}>{jobStatusLabel[job.status]}</b><ArrowRight /></span>
  </Link>
}
