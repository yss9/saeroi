import { ArrowRight, Clock, Package } from '@phosphor-icons/react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import type { ProcessingJobDetail } from '../api/processingJobApi.ts'
import type { ProcessingRequestListItem } from '../api/processingRequestApi.ts'
import { useProcessingJobs } from '../hooks/useProcessingJobs.ts'
import { useProcessorRequests } from '../hooks/useProcessorRequests.ts'
import { PROCESS_TYPE_LABEL, type ProcessingJobStatus } from '../types/processing.types.ts'
import { parseServerTime } from '../utils/processingTime.ts'
import './processor-work-list.css'

const jobStatusLabel: Record<ProcessingJobStatus, string> = {
  CREATED: '결제 대기', PAID: '수락 대기', ACCEPTED: '발송 대기', DECLINED: '거절', SHIPPED: '수령 대기', RECEIVED: '가공 시작 대기',
  PROCESSING: '가공 중', RESULT_SUBMITTED: '검토 대기', RETURN_SHIPPED: '결과물 반환 중', CONFIRMED: '완료', REJECTED: '보완 요청', EXPIRED: '만료', CANCELED: '반환 종료',
}
const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const formatDate = (value: string) => parseServerTime(value).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '목록을 불러오지 못했어요.'

/**
 * 가공사의 일 목록.
 *
 * **견적 요청 칸이 없다.** 견적을 쓰는 단계가 사라져, 결제되기 전의 요청은 가공사가 손댈
 * 데가 없다 — 맡을지 말지는 결제가 끝난 작업에서 정한다(`PAID` → 수락·거절). 요청 줄을
 * 남겨 두면 "견적 작성 전" 이라고 적힌, 아무것도 할 수 없는 줄만 목록을 채운다.
 *
 * 요청은 그래도 읽는다 — 작업 줄에 브랜드·소재·공정을 적는 데 쓴다. 한 쪽(20건)만 받으므로
 * 오래된 작업은 이름 없이 그려지고, 그 자리는 상세에서 확인하도록 안내한다.
 */
export function ProcessorWorkList() {
  const requests = useProcessorRequests()
  const jobs = useProcessingJobs()
  const requestById = useMemo(
    () => new Map((requests.data?.items ?? []).map(request => [request.requestId, request])),
    [requests.data?.items],
  )
  const rows = useMemo(
    () => [...(jobs.data ?? [])].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)),
    [jobs.data],
  )

  return <section className="pw-shell">
    <h1 className="ui-visually-hidden">가공 작업</h1>

    {jobs.error !== null && <div className="pw-error" role="alert">
      <span>{errorMessage(jobs.error)}</span>
      <button type="button" onClick={() => { void jobs.refetch() }}>다시 시도</button>
    </div>}

    {jobs.isPending ? <div className="pw-state" role="status">가공 작업을 불러오는 중이에요…</div>
      : rows.length === 0 ? <div className="pw-state"><Package size={36} /><strong>맡은 가공 작업이 없어요</strong></div>
        : <div className="pw-list" role="table" aria-label="가공 작업 목록">
          <div className="pw-row pw-row--head" role="row">
            <span>구분 / 번호</span><span>업체·소재 / 공정</span><span>수량·비용</span><span>접수일</span><span>상태</span>
          </div>
          {rows.map(job => <JobRow job={job} request={requestById.get(job.requestId)} key={`job-${job.jobId}`} />)}
        </div>}
  </section>
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
