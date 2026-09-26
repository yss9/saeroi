import { CheckCircle, Clock, CreditCard } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import type { ProcessingJob } from '../api/processingRequestApi.ts'
import { useProcessingPayment } from '../hooks/useProcessingPayment.ts'
import { formatDateTime } from '../utils/processingTime.ts'

const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const date = formatDateTime
const message = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '결제를 진행하지 못했어요.'

type PayableProcessingJob = Pick<ProcessingJob, 'jobId' | 'jobCode' | 'fee' | 'paymentExpiresAt'>

export function ProcessingPaymentEntry({ job }: { job: PayableProcessingJob }) {
  const payment = useProcessingPayment()

  if (payment.phase === 'SUCCEEDED' && payment.paidJob) {
    return <div className="br-payment br-payment--success" role="status"><CheckCircle aria-hidden="true" /><div><strong>가공비 결제가 완료되었습니다.</strong><span>{won(payment.paidJob.paymentAmount)} · 가공사가 수락하면 소재를 보낼 수 있어요.</span><Link to={`/processing/jobs/${payment.paidJob.jobId}`}>가공 작업 보기</Link></div></div>
  }

  return (
    <section className="br-payment" aria-labelledby="processing-payment-title">
      <div className="br-payment__title"><CreditCard aria-hidden="true" /><div><h3 id="processing-payment-title">가공비 결제</h3></div></div>
      <dl><div><dt>결제 금액</dt><dd>{won(job.fee)}</dd></div><div><dt>결제 기한</dt><dd>{date(job.paymentExpiresAt)}</dd></div></dl>
      <p className="br-payment__deadline"><Clock aria-hidden="true" />기한 내 결제하지 않으면 Job과 소재 Lock이 자동으로 만료됩니다.</p>
      {payment.phase === 'FAILED' && <p className="br-payment__error" role="alert">{message(payment.error)}</p>}
      {payment.phase === 'AWAITING' && payment.ready?.live === false ? (
        <div className="br-payment__mock">
          <p>테스트 결제 환경입니다. 실제 금액은 청구되지 않습니다.</p>
          <button type="button" disabled={payment.isBusy} onClick={() => void payment.approve(job.jobId)}>결제 완료로 처리 (시연)</button>
        </div>
      ) : (
        <button className="br-payment__start" type="button" disabled={payment.isBusy} onClick={() => void payment.start(job.jobId)}>
          {payment.phase === 'PREPARING' ? '결제 준비 중…' : '결제 진행'}
        </button>
      )}
    </section>
  )
}
