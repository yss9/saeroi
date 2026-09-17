import { ArrowLeft, CalendarBlank, CheckCircle, Clock, Factory, Package, Receipt } from '@phosphor-icons/react'
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
import { ProcessingShipmentForm } from './ProcessingShipmentForm.tsx'
import { formatDateTime } from '../utils/processingTime.ts'
import './brand-processing-detail.css'

const quoteStatusLabel: Record<ProcessingQuoteStatus, string> = {
  SUBMITTED: '선택 가능', SELECTED: '선택 완료', REJECTED: '미선택', EXPIRED: '만료',
}
const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const date = formatDateTime
const message = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '정보를 불러오지 못했어요.'

interface Props { requestId: number }

export function BrandProcessingRequestDetail({ requestId }: Props) {
  const [confirmingQuoteId, setConfirmingQuoteId] = useState<number | null>(null)
  const { detail, quotes, selection, job, ship } = useBrandProcessingRequestDetail(requestId)

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
  // 고른 견적. 방금 누른 것은 아직 목록 상태가 갱신되기 전이라 함께 본다.
  const chosen =
    quotes.data?.find(quote => quote.quoteId === selection.variables) ??
    quotes.data?.find(quote => quote.status === 'SELECTED')

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
        {/* 표시는 가공사(Factory)와 달라야 한다 — 같은 그림이 두 뜻을 가리키면 흐려진다.
            여기는 "받은 견적서" 다. */}
        <div className="br-detail__section-title">
          <div>
            <Receipt aria-hidden="true" />
            <h2 id="quotes-title">{chosen ? '선택한 견적' : '접수된 견적'}</h2>
          </div>
          {!chosen && <span>{quotes.data?.length ?? 0}건</span>}
        </div>

        {selection.data && (
          <>
            <div className="br-detail__selected" role="status">
              <CheckCircle aria-hidden="true" />
              {/* 작업 번호는 읽어서 얻을 것이 없고, 결제 기한은 바로 아래 결제 칸이
                  이미 말한다 — 같은 값을 두 줄에 적지 않는다. */}
              <div><strong>견적 선택이 완료되었습니다.</strong></div>
            </div>
          </>
        )}
        {/*
          **누구에게 맡겼는지를 결제 위에 둔다.** 아래 견적 목록은 고르고 나면 지나가 버려,
          결제 버튼 앞에서 "이 금액이 어느 업체의 것인가" 를 다시 확인할 곳이 없었다.
        */}
        {chosen && (
          <section className="br-detail__chosen" aria-label="선택한 가공업체">
            <div className="br-detail__chosen-head">
              <Factory aria-hidden="true" />
              <div>
                <strong>{chosen.processorName}</strong>
                <span>{request.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')}</span>
              </div>
            </div>
            <dl>
              <div><dt>견적 금액</dt><dd>{won(chosen.price)}</dd></div>
              <div><dt>예상 소요 시간</dt><dd>{chosen.leadDays}일</dd></div>
              <div><dt>요청 수량</dt><dd>{grams(request.quantity)}</dd></div>
            </dl>
            {chosen.memo && <p>{chosen.memo}</p>}
          </section>
        )}

        {paymentJob && <ProcessingPaymentEntry job={paymentJob} />}
        {selection.isError && <div className="br-detail__selection-error" role="alert">{message(selection.error)}</div>}

        {/*
          **고르고 나면 목록을 두지 않는다.** 위의 카드가 고른 업체와 금액을 이미 말하고,
          아래에 같은 내용이 한 번 더 있으면 어느 쪽이 지금 것인지 되짚어야 한다.
          고를 수 없게 된 견적(미선택)을 남겨 둘 이유도 없다.
        */}
        {chosen ? null : !quotes.data?.length ? (
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
        <ProcessingShipmentForm isPending={ship.isPending} error={ship.error} onSubmit={input => ship.mutate(input)} />
      )}
      {/*
        진행 상황과 수령 확인은 이 화면 안에서 끝난다 (가공 진행 화면은 없앴다).

        **만료된 작업에는 두지 않는다.** 결제 기한이 지나 소재 Lock 까지 풀린 건이라 진행할
        것도, 받을 것도 없다 — 빈 진행 칸은 아직 무언가 남아 있다는 오해만 만든다.
      */}
      {job && !['CREATED', 'PAID', 'EXPIRED'].includes(job.status) && (
        <BrandProcessingJobPanel jobId={job.jobId} />
      )}

      {/*
        **견적을 기다리는 동안만 둔다.** "언제까지 유효한가" 는 견적을 받는 창이 열려 있을
        때의 이야기다 — 이미 고른 요청이나 만료·취소된 요청에서는 지난 일을 다시 알리는
        칸이 되고, 만료된 화면에서는 아직 유효한 것처럼 읽히기까지 한다.
      */}
      {request.status === 'QUOTING' && (
        <aside className="br-detail__notice"><CalendarBlank aria-hidden="true" /><p>요청은 {date(request.expiresAt)}까지 유효합니다. 견적 선택 시 현재 요청 Lock은 생성된 Job에 연결되어 결제가 완료될 때까지 유지됩니다.</p></aside>
      )}
    </section>
  )
}
