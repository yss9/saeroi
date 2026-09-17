import { ArrowLeft, CalendarBlank, CheckCircle, Clock, Factory, Package } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import type { SubmittedProcessingQuote } from '../api/processingRequestApi.ts'
import { useBrandProcessingRequestDetail } from '../hooks/useBrandProcessingRequestDetail.ts'
import { PROCESS_TYPE_LABEL, type ProcessingQuoteStatus } from '../types/processing.types.ts'
import { progressLabel, progressTone } from '../utils/processingProgress.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { BrandProcessingJobPanel } from './BrandProcessingJobPanel.tsx'
import { ProcessingPaymentEntry } from './ProcessingPaymentEntry.tsx'
import './brand-processing-detail.css'

const quoteStatusLabel: Record<ProcessingQuoteStatus, string> = {
  SUBMITTED: '선택 가능', SELECTED: '선택 완료', REJECTED: '미선택', EXPIRED: '만료',
}
const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const date = (value: string) => new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`)
  .toLocaleString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const message = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '정보를 불러오지 못했어요.'

interface Props { requestId: number }

export function BrandProcessingRequestDetail({ requestId }: Props) {
  const [confirmingQuoteId, setConfirmingQuoteId] = useState<number | null>(null)
  const { detail, quotes, selection, job } = useBrandProcessingRequestDetail(requestId)

  if (detail.isPending || quotes.isPending) {
    return <div className="br-detail__state" role="status">요청과 견적을 불러오는 중이에요…</div>
  }
  if (detail.isError || quotes.isError) {
    const error = detail.error ?? quotes.error
    return (
      <div className="br-detail__state br-detail__state--error" role="alert">
        <strong>상세 정보를 불러오지 못했어요</strong><p>{message(error)}</p>
        <button type="button" onClick={() => { void detail.refetch(); void quotes.refetch() }}>다시 시도</button>
      </div>
    )
  }
  if (!detail.data) return null

  const request = detail.data
  const canSelect = request.status === 'QUOTING' && !selection.data
  const paymentJob = selection.data ?? (job?.status === 'CREATED' ? job : null)

  function select(quote: SubmittedProcessingQuote) {
    selection.mutate(quote.quoteId, { onSuccess: () => setConfirmingQuoteId(null) })
  }

  return (
    <section className="br-detail">
      <Link className="br-detail__back" to="/brand/processing/requests"><ArrowLeft aria-hidden="true" />요청 내역</Link>
      <header className="br-detail__heading">
        {/* 요청 번호(`PR` + UUID 28자리)는 제목이 될 수 없다 — 읽어서 얻을 것이 없다.
            어느 요청인지는 소재 이름이 말한다. */}
        <div><span>PROCESSING REQUEST DETAIL</span><h1>{request.materialName}</h1></div>
        {/* 견적을 수락한 뒤로 요청 상태는 움직이지 않는다 — 그 뒤의 일은 작업이 말한다. */}
        <span className={`br-detail__request-status is-${progressTone(request.status, job?.status)}`}>
          {progressLabel(request.status, job?.status)}
        </span>
      </header>

      <section className="br-detail__request-card" aria-labelledby="request-information-title">
        <div className="br-detail__section-title">
          <div><Package aria-hidden="true" /><h2 id="request-information-title">요청 정보</h2></div>
          {/* 잠긴 수량은 아래 "요청 수량" 이 이미 말한다. 이 자리에는 언제 보낸 요청인지를 둔다. */}
          <span><Clock aria-hidden="true" />{date(request.createdAt)} 요청</span>
        </div>
        <dl className="br-detail__request-grid">
          {/* 소재 이름은 제목이 이미 말한다 — 종류와 코드만 둔다. */}
          <div><dt>소재</dt><dd><strong>{MATERIAL_TYPE_LABEL[request.materialType]}</strong><span>{request.materialCode}</span></dd></div>
          <div><dt>가공업체</dt><dd><strong>{request.processorName}</strong></dd></div>
          <div><dt>요청 수량</dt><dd><strong>{grams(request.quantity)}</strong></dd></div>
          <div><dt>요청 만료</dt><dd><strong>{date(request.expiresAt)}</strong></dd></div>
        </dl>
        <div className="br-detail__processes"><span>요청 가공</span><div>{request.processTypes.map(type => <strong key={type}>{PROCESS_TYPE_LABEL[type]}</strong>)}</div></div>
      </section>

      <section className="br-detail__quotes" aria-labelledby="quotes-title">
        <div className="br-detail__section-title"><div><Factory aria-hidden="true" /><h2 id="quotes-title">접수된 견적</h2></div><span>{quotes.data?.length ?? 0}건</span></div>

        {selection.data && (
          <>
            <div className="br-detail__selected" role="status">
              <CheckCircle aria-hidden="true" />
              <div><strong>견적 선택이 완료되었습니다.</strong><span>생성된 작업 <b>{selection.data.jobCode}</b> · 결제 기한 {date(selection.data.paymentExpiresAt)}</span></div>
            </div>
          </>
        )}
        {paymentJob && <ProcessingPaymentEntry job={paymentJob} />}
        {selection.isError && <div className="br-detail__selection-error" role="alert">{message(selection.error)}</div>}

        {!quotes.data?.length ? (
          <div className="br-detail__empty"><Clock aria-hidden="true" /><strong>아직 접수된 견적이 없어요</strong><p>지정한 가공사가 견적을 제출하면 이곳에서 확인할 수 있습니다.</p></div>
        ) : (
          <div className="br-detail__quote-list">
            {quotes.data.map(quote => (
              <article className={`br-detail__quote is-${quote.status.toLowerCase()}`} key={quote.quoteId}>
                <header><div><Factory aria-hidden="true" /><div><strong>{quote.processorName}</strong><span>{date(quote.createdAt)} 제출</span></div></div><span>{quoteStatusLabel[quote.status]}</span></header>
                <div className="br-detail__quote-terms"><div><span>견적 금액</span><strong>{won(quote.price)}</strong></div><div><span>예상 소요일</span><strong>예상 {quote.leadDays}일</strong></div></div>
                <p>{quote.memo || '가공사가 별도의 메모를 남기지 않았습니다.'}</p>
                {canSelect && quote.status === 'SUBMITTED' && (
                  confirmingQuoteId === quote.quoteId ? (
                    <div className="br-detail__confirm">
                      <p>견적을 확정하면 15분 안에 결제를 완료해야 합니다.</p>
                      <div><button type="button" onClick={() => setConfirmingQuoteId(null)}>취소</button><button type="button" disabled={selection.isPending} onClick={() => select(quote)}>{selection.isPending ? '선택 중…' : '견적 선택 확정'}</button></div>
                    </div>
                  ) : <button className="br-detail__select" type="button" onClick={() => setConfirmingQuoteId(quote.quoteId)}>이 견적 선택</button>
                )}
              </article>
            ))}
          </div>
        )}
      </section>

      {job?.status === 'PAID' && (
        <div className="br-detail__job-link">
          {/* 소재 발송은 온체인 작업 등록 → Processor 수락 → 발송 서명까지 여러 단계를 거친다.
              그 단계는 작업 상세(useProcessingJobs)에만 있다 — 여기서 다시 만들면
              온체인 등록 없이 상태만 SHIPPED로 바뀌는 두 번째 발송 경로가 생긴다. */}
          <span>결제가 완료되었습니다. 소재 발송은 작업 상세에서 온체인으로 진행합니다.</span>
          <Link to={`/processing/jobs/${job.jobId}`}>소재 발송하기</Link>
        </div>
      )}
      {/* 진행 상황과 수령 확인은 이 화면 안에서 끝난다 (가공 진행 화면은 없앴다). */}
      {job && job.status !== 'CREATED' && job.status !== 'PAID' && (
        <BrandProcessingJobPanel jobId={job.jobId} />
      )}

      <aside className="br-detail__notice"><CalendarBlank aria-hidden="true" /><p>요청은 {date(request.expiresAt)}까지 유효합니다. 견적 선택 시 현재 요청 Lock은 생성된 Job에 연결되어 결제가 완료될 때까지 유지됩니다.</p></aside>
    </section>
  )
}
