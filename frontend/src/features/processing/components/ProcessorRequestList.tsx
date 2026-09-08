import { CaretDown, CaretLeft, CaretRight, ChartBar, Clock, Factory, FileText, Gear, Leaf, Package, SquaresFour, UserCircle } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../stores/authStore.ts'
import { isAppError } from '../../../utils/errors.ts'
import { PROCESS_TYPE_LABEL, type ProcessingRequestStatus } from '../types/processing.types.ts'
import { useProcessorRequests } from '../hooks/useProcessorRequests.ts'
import { ProcessorQuoteForm } from './ProcessorQuoteForm.tsx'
import './processor-requests.css'

const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const date = (value: string) => new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`).toLocaleString('ko-KR', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '요청 목록을 불러오지 못했어요.'
const statusOptions: { value?: ProcessingRequestStatus; label: string }[] = [
  { label: '전체' },
  { value: 'QUOTING', label: '견적 대기' },
  { value: 'SELECTED', label: '선택' },
  { value: 'CANCELED', label: '취소' },
  { value: 'REJECTED', label: '거절' },
  { value: 'EXPIRED', label: '만료' },
]
const statusLabel: Record<ProcessingRequestStatus, string> = {
  QUOTING: '견적 대기', SELECTED: '선택 완료', CANCELED: '취소', REJECTED: '거절', EXPIRED: '만료',
}

export function ProcessorRequestList() {
  const requests = useProcessorRequests()
  const user = useAuthStore(state => state.user)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const pageCount = requests.data ? Math.max(1, Math.ceil(requests.data.total / requests.data.size)) : 1

  return <div className="pr-shell">
    <aside className="pr-sidebar" aria-label="Processor 메뉴">
      <Link className="pr-logo" to="/"><span><Leaf size={19} weight="fill" /></span><div>SAEROI<small>PROCESSOR</small></div></Link>
      <nav>
        <Link to="/processor/jobs"><SquaresFour size={20} weight="fill" />Dashboard</Link>
        <Link to="/processor/jobs" aria-current="page"><FileText size={20} weight="fill" />견적 요청</Link>
        <span><Factory size={20} />가공 관리</span>
        <span><ChartBar size={20} />결과 관리</span>
      </nav>
      <div className="pr-sidebar-bottom"><span><Gear size={19} />Settings</span><div className="pr-profile"><UserCircle size={32} weight="duotone" /><div><strong>{user?.name ?? 'Processor'}</strong><small>가공사 계정</small></div></div></div>
    </aside>
    <div className="pr-main">
      <header className="pr-header"><span>Processor Workspace <b>/ 견적 요청</b></span><span className="pr-role">PROCESSOR</span></header>
      <main className="pr-content">
        <div className="pr-heading"><div><span>PROCESSING REQUESTS</span><h1>견적 요청</h1><p>Brand가 보낸 가공 요청을 확인하고 조건을 검토하세요.</p></div><div className="pr-heading-icon"><FileText size={28} weight="duotone" /></div></div>
        <section className="pr-summary" aria-label="요청 현황">
          <article><span>조회 결과</span><strong>{requests.data?.total ?? 0}<small>건</small></strong><p>현재 조건에 맞는 요청</p></article>
          <article><span>현재 페이지 · 견적 대기</span><strong>{requests.data?.items.filter(item => item.status === 'QUOTING').length ?? 0}<small>건</small></strong><p>견적 검토가 필요해요</p></article>
          <article><span>현재 페이지 · 선택 완료</span><strong>{requests.data?.items.filter(item => item.status === 'SELECTED').length ?? 0}<small>건</small></strong><p>진행이 확정된 요청</p></article>
        </section>
        <div className="pr-filter" aria-label="요청 상태 필터">
          {statusOptions.map(option => <button key={option.value ?? 'ALL'} className={requests.status === option.value ? 'is-active' : ''} onClick={() => requests.selectStatus(option.value)}>{option.label}</button>)}
        </div>
        {requests.isPending ? <section className="pr-state" role="status">요청 목록을 불러오는 중이에요…</section>
          : requests.isError ? <section className="pr-state pr-error" role="alert"><strong>목록을 불러오지 못했어요</strong><p>{errorMessage(requests.error)}</p><button onClick={() => void requests.refetch()}>다시 시도</button></section>
          : !requests.data?.items.length ? <section className="pr-state"><FileText size={38} /><strong>{requests.status ? '조건에 맞는 요청이 없어요' : '아직 들어온 요청이 없어요'}</strong><p>{requests.status ? '다른 상태 필터를 선택해 요청을 확인해 보세요.' : '새 요청이 도착하면 이곳에서 확인할 수 있어요.'}</p></section>
          : <section className="pr-list-card">
            <div className="pr-list-title"><div><h2>들어온 요청</h2><p>총 {requests.data.total}건의 가공 요청이 있어요.</p></div><span>{requests.data.total} REQUESTS</span></div>
            <div className="pr-table" role="table" aria-label="가공 요청 목록">
              <div className="pr-row pr-table-head" role="row"><span>요청 정보</span><span>소재 / 가공</span><span>수량</span><span>요청일</span><span>상태</span></div>
              {requests.data.items.map(item => <div className="pr-entry" key={item.requestId}><article className="pr-row" role="row">
                <div className="pr-request"><span className="pr-code">{item.requestCode}</span><strong>{item.brandName}</strong></div>
                <div className="pr-material"><span className="pr-material-icon"><Package size={20} weight="duotone" /></span><div><strong>{item.materialName}</strong><small>{item.materialCode}</small><div>{item.processTypes.map(type => <span key={type}>{PROCESS_TYPE_LABEL[type]}</span>)}</div></div></div>
                <strong className="pr-quantity">{grams(item.quantity)}</strong>
                <span className="pr-date"><Clock size={15} />{date(item.createdAt)}</span>
                <div className="pr-row-action"><span className={`pr-status is-${item.status.toLowerCase()}`}>{statusLabel[item.status]}</span><button aria-label={`${item.requestCode} 요청 상세 보기`} aria-expanded={expandedId === item.requestId} onClick={() => setExpandedId(current => current === item.requestId ? null : item.requestId)}><CaretDown size={15} /></button></div>
              </article>
              {expandedId === item.requestId && <div className="pr-detail">
                <div><span>소재 유형</span><strong>{item.materialType}</strong></div>
                <div><span>요청 수량</span><strong>{grams(item.quantity)}</strong></div>
                <div><span>요청 일시</span><strong>{date(item.createdAt)}</strong></div>
                <div><span>만료 일시</span><strong>{date(item.expiresAt)}</strong></div>
                {item.status === 'QUOTING' ? <ProcessorQuoteForm requestId={item.requestId} requestCode={item.requestCode} /> : <p>이 요청은 현재 견적을 작성할 수 없는 상태입니다.</p>}
              </div>}</div>)}
            </div>
            {pageCount > 1 && <nav className="pr-pagination" aria-label="요청 목록 페이지">
              <button aria-label="이전 페이지" disabled={requests.page === 0} onClick={() => requests.setPage(requests.page - 1)}><CaretLeft size={16} /></button>
              <span><b>{requests.page + 1}</b> / {pageCount}</span>
              <button aria-label="다음 페이지" disabled={requests.page + 1 >= pageCount} onClick={() => requests.setPage(requests.page + 1)}><CaretRight size={16} /></button>
            </nav>}
          </section>}
      </main>
    </div>
  </div>
}
