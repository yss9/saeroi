import { ArrowLeft, CalendarBlank, CheckCircle, Clock, Factory, LockKey, Package } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import type { SubmittedProcessingQuote } from '../api/processingRequestApi.ts'
import { useBrandProcessingRequestDetail } from '../hooks/useBrandProcessingRequestDetail.ts'
import { PROCESS_TYPE_LABEL, type ProcessingQuoteStatus, type ProcessingRequestStatus } from '../types/processing.types.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { ProcessingPaymentEntry } from './ProcessingPaymentEntry.tsx'
import './brand-processing-detail.css'

const requestStatusLabel: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기', SELECTED: '선택 완료', CANCELED: '취소', REJECTED: '거절', EXPIRED: '만료',
}
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
  const { detail, quotes, selection } = useBrandProcessingRequestDetail(requestId)

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

  function select(quote: SubmittedProcessingQuote) {
    selection.mutate(quote.quoteId, { onSuccess: () => setConfirmingQuoteId(null) })
  }

  return (
    <section className="br-detail">
      <Link className="br-detail__back" to="/brand/processing/requests"><ArrowLeft aria-hidden="true" />요청 목록</Link>
      <header className="br-detail__heading">
        <div><span>PROCESSING REQUEST DETAIL</span><h1>{request.requestCode}</h1><p>가공 요청 정보와 제출된 견적을 확인하세요.</p></div>
        <span className={`br-detail__request-status is-${request.status.toLowerCase()}`}>{requestStatusLabel[request.status]}</span>
      </header>

      <section className="br-detail__request-card" aria-labelledby="request-information-title">
        <div className="br-detail__section-title">
          <div><Package aria-hidden="true" /><h2 id="request-information-title">요청 정보</h2></div>
          <span><LockKey aria-hidden="true" />{grams(request.quantity)} 예약 중</span>
        </div>
        <dl className="br-detail__request-grid">
          <div><dt>소재</dt><dd><strong>{request.materialName}</strong><span>{request.materialCode}</span><span>{MATERIAL_TYPE_LABEL[request.materialType]}</span></dd></div>
          <div><dt>지정 가공사</dt><dd><strong>{request.processorName}</strong><span>Processor #{request.processorId}</span></dd></div>
          <div><dt>요청 수량</dt><dd><strong>{grams(request.quantity)}</strong><span>백엔드 Lock 유지 수량</span></dd></div>
          <div><dt>요청 기간</dt><dd><strong>{date(request.createdAt)}</strong><span>{date(request.expiresAt)} 만료</span></dd></div>
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
            <ProcessingPaymentEntry job={selection.data} />
          </>
        )}
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

      <aside className="br-detail__notice"><CalendarBlank aria-hidden="true" /><p>요청은 {date(request.expiresAt)}까지 유효합니다. 견적 선택 시 현재 요청 Lock은 생성된 Job에 연결되어 결제가 완료될 때까지 유지됩니다.</p></aside>
    </section>
  )
}
