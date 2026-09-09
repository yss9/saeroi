import { ArrowLeft, ArrowRight, Factory, FileText, Package, Plus, Storefront } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { useBrandProcessingRequests } from '../hooks/useBrandProcessingRequests.ts'
import { PROCESS_TYPE_LABEL, type ProcessingRequestStatus } from '../types/processing.types.ts'
import './brand-processing-list.css'

const statusOptions: { value?: ProcessingRequestStatus; label: string }[] = [
  { label: '전체' },
  { value: 'QUOTING', label: '견적 대기' },
  { value: 'SELECTED', label: '선택 완료' },
  { value: 'CANCELED', label: '취소' },
  { value: 'REJECTED', label: '거절' },
  { value: 'EXPIRED', label: '만료' },
]

const statusLabel: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기',
  SELECTED: '선택 완료',
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
  const requests = useBrandProcessingRequests()
  const pageCount = requests.data
    ? Math.max(1, Math.ceil(requests.data.total / requests.data.size))
    : 1

  return (
    <section className="br-list">
      <header className="br-list__heading">
        <div>
          <span>PROCESSING REQUESTS</span>
          <h1>가공 요청 내역</h1>
          <p>내가 요청한 소재 가공의 견적 진행 상태와 지정 가공사를 확인하세요.</p>
        </div>
        <Link to="/brand/processing"><Plus aria-hidden="true" />새 가공 신청</Link>
      </header>

      <section className="br-list__summary" aria-label="요청 현황">
        <article><FileText aria-hidden="true" /><div><span>전체 요청</span><strong>{requests.data?.total ?? 0}<small>건</small></strong></div></article>
        <article><Factory aria-hidden="true" /><div><span>현재 페이지 · 견적 대기</span><strong>{requests.data?.items.filter(item => item.status === 'QUOTING').length ?? 0}<small>건</small></strong></div></article>
        <article><Storefront aria-hidden="true" /><div><span>현재 페이지 · 선택 완료</span><strong>{requests.data?.items.filter(item => item.status === 'SELECTED').length ?? 0}<small>건</small></strong></div></article>
      </section>

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
              <span>요청</span><span>소재·가공</span><span>지정 가공사</span><span>수량</span><span>기간</span><span>상태</span><span>견적</span>
            </div>
            {requests.data.items.map(item => (
              <article className="br-list__row" role="row" key={item.requestId}>
                <div className="br-list__request"><strong>{item.requestCode}</strong><span>{localDate(item.createdAt)} 요청</span></div>
                <div className="br-list__material">
                  <strong>{item.materialName}</strong>
                  <span>{item.materialCode}</span>
                  <span>{item.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')}</span>
                </div>
                <div className="br-list__processor"><Storefront aria-hidden="true" /><span>{item.processorName}</span></div>
                <strong className="br-list__quantity">{grams(item.quantity)}</strong>
                <div className="br-list__date"><span>요청 {localDate(item.createdAt)}</span><span>만료 {localDate(item.expiresAt)}</span></div>
                <span className={`br-list__status is-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span>
                <Link className="br-list__quote" to={`/brand/processing/requests/${item.requestId}`}>견적 확인</Link>
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
