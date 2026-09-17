import { ArrowLeft, ArrowRight, CaretRight, CheckCircle, Factory, FileText, Package, Plus, Storefront } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import { useBrandProcessingRequests } from '../hooks/useBrandProcessingRequests.ts'
import { useBrandProcessingRequestSummary } from '../hooks/useBrandProcessingRequestSummary.ts'
import { useProcessingJobs } from '../hooks/useProcessingJobs.ts'
import { PROCESS_TYPE_LABEL, type ProcessingRequestStatus } from '../types/processing.types.ts'
import { progressLabel, progressTone } from '../utils/processingProgress.ts'
import './brand-processing-list.css'

const statusOptions: { value?: ProcessingRequestStatus; label: string }[] = [
  { label: '전체' },
  { value: 'QUOTING', label: '견적 대기' },
  { value: 'SELECTED', label: '견적 수락' },
  { value: 'CANCELED', label: '취소' },
  { value: 'REJECTED', label: '거절' },
  { value: 'EXPIRED', label: '만료' },
]

const grams = (quantity: number) => `${quantity.toLocaleString('ko-KR')} g`
const localDate = (value: string) => new Date(
  /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`,
).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '요청 목록을 불러오지 못했어요.'

export function BrandProcessingRequestList() {
  const followClick = useClickNavigation()
  const requests = useBrandProcessingRequests()
  /*
    **작업 상태를 함께 읽는다.** 견적을 수락한 뒤로 요청은 `SELECTED` 에서 움직이지 않고
    결제·발송·가공·반환은 전부 작업에 적힌다 — 요청 상태만 보여주면 가공이 끝났는데도
    목록은 "견적 수락" 이라고 말한다. 목록 화면이 둘을 합쳐 한 칸으로 보여준다.
  */
  const jobs = useProcessingJobs()
  const jobOf = (requestId: number) =>
    jobs.data?.find(job => job.requestId === requestId)?.status
  // 아래 필터와 무관한 숫자다 — 무엇을 누르든 위는 그대로여야 한다.
  const summary = useBrandProcessingRequestSummary()
  const pageCount = requests.data
    ? Math.max(1, Math.ceil(requests.data.total / requests.data.size))
    : 1

  return (
    <section className="br-list">
      <h1 className="ui-visually-hidden">가공 요청 내역</h1>

      {/*
        일이 흘러가는 순서대로 넷. 견적을 받고 → 수락해 맡기고 → 가공이 끝나고 → 받는다.
        한 건은 그중 한 칸에만 선다 — 겹쳐 세면 합이 전체보다 커져 무엇을 세는 숫자인지
        알 수 없다.
      */}
      <section className="br-list__summary" aria-label="요청 현황" aria-busy={summary.isPending}>
        <article><FileText aria-hidden="true" /><div><span>견적 대기</span><strong>{summary.quoting}<small>건</small></strong></div></article>
        <article><Storefront aria-hidden="true" /><div><span>견적 수락</span><strong>{summary.selected}<small>건</small></strong></div></article>
        <article><Factory aria-hidden="true" /><div><span>가공 완료</span><strong>{summary.processed}<small>건</small></strong></div></article>
        <article><CheckCircle aria-hidden="true" /><div><span>수령 완료</span><strong>{summary.received}<small>건</small></strong></div></article>
      </section>

      {/* 새 요청을 만드는 자리는 목록 바로 위다 — 맨 위에 있으면 목록을 훑고 나서
          다시 화면 꼭대기로 올라가야 한다. */}
      <div className="br-list__toolbar">
      <div className="br-list__filters" aria-label="요청 상태 필터">
        {statusOptions.map(option => (
          <button
            key={option.value ?? 'ALL'}
            type="button"
            className={requests.status === option.value ? 'is-active' : ''}
            onClick={() => requests.selectStatus(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

        <Link className="br-list__new" to="/brand/processing">
          <Plus aria-hidden="true" />새 가공 신청
        </Link>
      </div>

      {requests.isPending ? (
        <div className="br-list__state" role="status">가공 요청을 불러오는 중이에요…</div>
      ) : requests.isError ? (
        <div className="br-list__state br-list__state--error" role="alert">
          <strong>요청 목록을 불러오지 못했어요</strong>
          <p>{errorMessage(requests.error)}</p>
          <button type="button" onClick={() => void requests.refetch()}>다시 시도</button>
        </div>
      ) : !requests.data?.items.length ? (
        <div className="br-list__state">
          <Package aria-hidden="true" />
          <strong>{requests.status ? '조건에 맞는 가공 요청이 없어요' : '아직 생성한 가공 요청이 없어요'}</strong>
          <p>{requests.status ? '다른 상태를 선택해 요청을 확인해 보세요.' : '보유 소재와 가공 조건을 선택해 첫 요청을 만들어 보세요.'}</p>
          {!requests.status && <Link to="/brand/processing">첫 가공 신청하기</Link>}
        </div>
      ) : (
        <div className="br-list__card">
          <div className="br-list__table" role="table" aria-label="내 가공 요청 목록">
            <div className="br-list__row br-list__row--head" role="row">
              <span>소재</span><span>가공</span><span>가공업체</span><span>수량</span><span>만료</span><span>상태</span><span aria-hidden="true" />
            </div>
            {requests.data.items.map(item => (
              <article className="br-list__row" role="row" key={item.requestId} onClick={(event) => followClick(event, `/brand/processing/requests/${item.requestId}`)}>
                {/*
                  요청 번호(`PR` + UUID 28자리)는 적지 않는다. 순번도 날짜도 아니라 읽어서
                  얻을 것이 없고, 요청 날짜는 옆의 기간 칸이 이미 말한다.
                */}
                <div className="br-list__material">
                  <strong>{item.materialName}</strong>
                  <span>{item.materialCode}</span>
                </div>
                <div className="br-list__process">
                  {item.processTypes.map(type => (
                    <b key={type}>{PROCESS_TYPE_LABEL[type]}</b>
                  ))}
                </div>
                <div className="br-list__processor"><Storefront aria-hidden="true" /><span>{item.processorName}</span></div>
                <strong className="br-list__quantity">{grams(item.quantity)}</strong>
                {/* 요청일은 이미 지난 일이라 목록에서 결정을 바꾸지 않는다. 남은 것은
                    "언제까지 견적을 받을 수 있는가" 다. */}
                <strong className="br-list__date">{localDate(item.expiresAt)}</strong>
                <span
                  className={`br-list__status is-${progressTone(item.status, jobOf(item.requestId))}`}
                >
                  {progressLabel(item.status, jobOf(item.requestId))}
                </span>
                <Link
                  className="br-list__quote"
                  to={`/brand/processing/requests/${item.requestId}`}
                  aria-label={`${item.materialName} 요청 상세 보기`}
                >
                  <CaretRight aria-hidden="true" />
                </Link>
              </article>
            ))}
          </div>

          {pageCount > 1 && (
            <nav className="br-list__pagination" aria-label="가공 요청 페이지">
              <button type="button" aria-label="이전 페이지" disabled={requests.page === 0} onClick={() => requests.setPage(requests.page - 1)}><ArrowLeft aria-hidden="true" /></button>
              <span><strong>{requests.page + 1}</strong> / {pageCount}</span>
              <button type="button" aria-label="다음 페이지" disabled={requests.page + 1 >= pageCount} onClick={() => requests.setPage(requests.page + 1)}><ArrowRight aria-hidden="true" /></button>
            </nav>
          )}
        </div>
      )}
    </section>
  )
}
