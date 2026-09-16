import { ArrowLeft, ArrowRight, CaretRight, Factory, FileText, Package, Plus, Storefront } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import { useBrandProcessingRequests } from '../hooks/useBrandProcessingRequests.ts'
import { useBrandProcessingRequestSummary } from '../hooks/useBrandProcessingRequestSummary.ts'
import { PROCESS_TYPE_LABEL, type ProcessingRequestStatus } from '../types/processing.types.ts'
import './brand-processing-list.css'

const statusOptions: { value?: ProcessingRequestStatus; label: string }[] = [
  { label: '전체' },
  { value: 'QUOTING', label: '견적 대기' },
  { value: 'SELECTED', label: '견적 수락' },
  { value: 'CANCELED', label: '취소' },
  { value: 'REJECTED', label: '거절' },
  { value: 'EXPIRED', label: '만료' },
]

const statusLabel: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기',
  SELECTED: '견적 수락',
  CANCELED: '취소',
  REJECTED: '거절',
  EXPIRED: '만료',
}

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
  // 아래 필터와 무관한 숫자다 — 무엇을 누르든 위는 그대로여야 한다.
  const summary = useBrandProcessingRequestSummary()
  const pageCount = requests.data
    ? Math.max(1, Math.ceil(requests.data.total / requests.data.size))
    : 1

  return (
    <section className="br-list">
      <h1 className="ui-visually-hidden">가공 요청 내역</h1>

      <section className="br-list__summary" aria-label="요청 현황" aria-busy={summary.isPending}>
        <article><FileText aria-hidden="true" /><div><span>전체 요청</span><strong>{summary.total}<small>건</small></strong></div></article>
        <article><Factory aria-hidden="true" /><div><span>견적 대기</span><strong>{summary.quoting}<small>건</small></strong></div></article>
        <article><Storefront aria-hidden="true" /><div><span>견적 수락</span><strong>{summary.selected}<small>건</small></strong></div></article>
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
                <span className={`br-list__status is-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span>
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
