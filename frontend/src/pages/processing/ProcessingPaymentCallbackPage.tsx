import { CheckCircle, WarningCircle } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { processingRequestApi, type ProcessingJob } from '../../features/processing/api/processingRequestApi.ts'
import { processingJobApi, type ProcessingJobDetail } from '../../features/processing/api/processingJobApi.ts'
import { PROCESSING_PAYMENT_JOB_KEY } from '../../features/processing/hooks/useProcessingPayment.ts'
import { isAppError } from '../../utils/errors.ts'
import '../../features/processing/components/brand-processing-detail.css'

const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const message = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '결제 승인에 실패했습니다.'

export function ProcessingPaymentCallbackPage() {
  const { jobId: jobIdParam } = useParams()
  const [searchParams] = useSearchParams()
  const jobId = Number(jobIdParam ?? readStoredJobId())
  const pgToken = searchParams.get('pg_token')
  const validJobId = Number.isSafeInteger(jobId) && jobId > 0
  const handled = useRef(false)
  const [job, setJob] = useState<ProcessingJob | null>(null)
  const [paymentJob, setPaymentJob] = useState<ProcessingJobDetail | null>(null)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!validJobId) return
    processingJobApi.detail(jobId).then(setPaymentJob).catch(() => {
      // 승인 결과 안내가 본문이고, Job 조회 실패가 그 안내까지 가리면 안 된다.
    })
  }, [jobId, validJobId])

  useEffect(() => {
    if (!validJobId || !pgToken || handled.current) return
    handled.current = true
    processingRequestApi.confirmPayment(jobId, pgToken)
      .then(result => { setJob(result); clearStoredJobId() })
      .catch(setError)
  }, [jobId, pgToken, validJobId])

  let content
  if (!validJobId) {
    content = <><WarningCircle aria-hidden="true" /><h1>결제 정보를 확인할 수 없습니다</h1><p>올바른 Job 번호가 아닙니다.</p></>
  } else if (!pgToken) {
    content = <><WarningCircle aria-hidden="true" /><h1>결제가 완료되지 않았습니다</h1><p>결제 기한 전이라면 요청 상세에서 다시 결제할 수 있습니다.</p></>
  } else if (error) {
    content = <><WarningCircle aria-hidden="true" /><h1>결제 승인에 실패했습니다</h1><p>{message(error)}</p></>
  } else if (!job) {
    content = <><span className="br-payment-callback__spinner" aria-hidden="true" /><h1>가공비 결제를 승인하고 있습니다</h1><p>잠시만 기다려 주세요.</p></>
  } else {
    content = <><CheckCircle aria-hidden="true" /><h1>가공비 결제가 완료되었습니다</h1><strong>{won(job.paymentAmount)}</strong><p>결제가 완료되었습니다. 소재를 가공사로 발송해 주세요.</p><div className="br-payment-callback__actions"><Link to={`/processing/jobs/${job.jobId}`}>가공 작업 보기</Link><Link to={`/brand/processing/requests/${job.requestId}`}>요청 상세로 돌아가기</Link></div></>
  }

  const canRetry = !job && validJobId && (!pgToken || Boolean(error)) && paymentJob?.status === 'CREATED'
  const requestPath = paymentJob ? `/brand/processing/requests/${paymentJob.requestId}` : null

  return <section className="br-payment-callback" role="status">{content}{!job && <div className="br-payment-callback__actions">{canRetry && requestPath && <Link to={requestPath}>결제 다시 시도</Link>}{!canRetry && requestPath && <Link to={requestPath}>요청 상세 확인</Link>}<Link to="/brand/processing/requests">요청 목록으로</Link></div>}</section>
}

function readStoredJobId() {
  try { return sessionStorage.getItem(PROCESSING_PAYMENT_JOB_KEY) } catch { return null }
}
function clearStoredJobId() {
  try { sessionStorage.removeItem(PROCESSING_PAYMENT_JOB_KEY) } catch { /* 경로로 복귀했으므로 실패해도 무방하다. */ }
}
