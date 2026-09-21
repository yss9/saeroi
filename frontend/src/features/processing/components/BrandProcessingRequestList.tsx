import { ArrowLeft, ArrowRight, CaretRight, Package, Plus, Storefront } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import { useBrandProcessingRequests } from '../hooks/useBrandProcessingRequests.ts'
import { useProcessingJobs } from '../hooks/useProcessingJobs.ts'
import {
  PROCESS_TYPE_LABEL,
  type ProcessingRequestStatus,
} from '../types/processing.types.ts'
import {
  PHASE_ORDER,
  phaseOf,
  progressLabel,
  progressTone,
  type ProcessingPhase,
} from '../utils/processingProgress.ts'
import { formatDate } from '../utils/processingTime.ts'
import './brand-processing-list.css'

/**
 * 목록을 거르는 축 — **지금 누구 차례인가.**
 *
 * **상태를 그대로 나열하지 않는다.** 요청 상태 다섯에 작업 상태 열까지 더하면 버튼이
 * 열댓 개가 되는데, 브랜드가 목록에서 찾는 것은 둘뿐이다 — 내가 손대야 하는 건과
 * 기다리면 되는 건. 어느 줄이 무슨 상태인지는 상태 칸이 이미 말하므로, 버튼은 훑을
 * 범위만 줄인다.
 *
 * ⚠️ **거르기는 화면이 한다.** 한 묶음이 요청 상태 여럿과 작업 상태 여럿에 걸쳐 있어
 * 서버의 `status` 하나로는 표현할 수 없다. 그래서 목록은 늘 전체를 받아 와 여기서
 * 거른다 — 요청이 한 쪽(20건)을 넘기면 다음 쪽의 건은 세지도, 걸러지지도 않는다.
 * 서버가 이 묶음(`phase`)을 받아 주면 그때 서버로 넘긴다.
 */
const FILTERS: readonly { key: 'ALL' | ProcessingPhase; label: string; empty: string }[] = [
  { key: 'ALL', label: '전체', empty: '아직 생성한 가공 요청이 없어요' },
  { key: 'MY_TURN', label: '내 차례', empty: '지금 손댈 요청이 없어요' },
  { key: 'IN_PROGRESS', label: '진행 중', empty: '진행 중인 요청이 없어요' },
  { key: 'DONE', label: '완료', empty: '수령을 마친 가공이 없어요' },
  { key: 'CLOSED', label: '종료', empty: '취소·거절·만료된 요청이 없어요' },
]

const grams = (quantity: number) => `${quantity.toLocaleString('ko-KR')} g`
const localDate = formatDate
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '요청 목록을 불러오지 못했어요.'

export function BrandProcessingRequestList() {
  const followClick = useClickNavigation()
  const requests = useBrandProcessingRequests()
  // 지금 고른 묶음. 서버는 이 축을 모르므로 목록은 늘 전체를 받아 온다.
  const [filterKey, setFilterKey] = useState<'ALL' | ProcessingPhase>('ALL')
  const filter = FILTERS.find((option) => option.key === filterKey) ?? FILTERS[0]
  /*
    **작업 상태를 함께 읽는다.** 요청은 만들어진 뒤 움직이지 않고 결제·수락·발송·가공·반환은
    전부 작업에 적힌다 — 요청 상태만 보여주면 가공이 끝났는데도 목록은 그대로다. 목록
    화면이 둘을 합쳐 한 칸으로 보여준다.
  */
  const jobs = useProcessingJobs()
  const jobOf = (requestId: number) =>
    jobs.data?.find(job => job.requestId === requestId)

  /*
    **끝난 줄에도 내 차례가 남아 있을 수 있다.** 거절·만료·취소는 서버가 DB 와 환불만
    정리하고, 체인의 소재 잠금은 브랜드 지갑이 직접 풀어야 한다 — 그래서 묶음을 정할 때
    상태만이 아니라 `chainReleaseRequired` 까지 본다.
  */
  const phaseFor = (item: { requestId: number; status: ProcessingRequestStatus }) => {
    const job = jobOf(item.requestId)
    return phaseOf(item.status, job?.status, job?.chainReleaseRequired ?? false)
  }

  const items = requests.data?.items ?? []
  const countOf = (key: 'ALL' | ProcessingPhase) =>
    key === 'ALL' ? items.length : items.filter((item) => phaseFor(item) === key).length

  /*
    거르고 나서 **내 차례를 맨 위로** 올린다. 손댈 건이 목록 아래에 흩어져 있으면 필터를
    누르지 않고는 찾을 수 없는데, 대부분의 방문은 "할 일 있나" 를 보러 오는 것이다.
    같은 묶음 안의 순서는 서버가 준 그대로 둔다 (sort 는 안정 정렬이다).
  */
  const shown = items
    .filter((item) => filterKey === 'ALL' || phaseFor(item) === filterKey)
    .sort((left, right) => PHASE_ORDER.indexOf(phaseFor(left)) - PHASE_ORDER.indexOf(phaseFor(right)))
  const pageCount = requests.data
    ? Math.max(1, Math.ceil(requests.data.total / requests.data.size))
    : 1

  return (
    <section className="br-list">
      <h1 className="ui-visually-hidden">가공 요청 내역</h1>

      {/* 새 요청을 만드는 자리는 목록 바로 위다 — 맨 위에 있으면 목록을 훑고 나서
          다시 화면 꼭대기로 올라가야 한다. */}
      <div className="br-list__toolbar">
      <div className="br-list__filters" aria-label="요청 상태 필터">
        {FILTERS.map((option) => (
          <button
            key={option.key}
            type="button"
            className={filterKey === option.key ? 'is-active' : ''}
            onClick={() => setFilterKey(option.key)}
          >
            {option.label} {countOf(option.key)}
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
      ) : !shown.length ? (
        <div className="br-list__state">
          <Package aria-hidden="true" />
          <strong>{filter.empty}</strong>
          <p>
            {filterKey === 'ALL'
              ? '보유 소재와 가공 조건을 선택해 첫 요청을 만들어 보세요.'
              : '다른 묶음을 눌러 요청을 확인해 보세요.'}
          </p>
          {filterKey === 'ALL' && <Link to="/brand/processing">첫 가공 신청하기</Link>}
        </div>
      ) : (
        <div className="br-list__card">
          <div className="br-list__table" role="table" aria-label="내 가공 요청 목록">
            <div className="br-list__row br-list__row--head" role="row">
              <span>소재</span><span>가공</span><span>가공업체</span><span>수량</span><span>만료</span><span>상태</span><span aria-hidden="true" />
            </div>
            {shown.map(item => (
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
                {/* 견적이 왔으면 상태 자리에서 그 사실을 먼저 말한다 — "견적 대기" 는
                    기다리라는 말이라 눌러 볼 이유가 없는 줄로 읽힌다. */}
                <span
                  className={`br-list__status is-${progressTone(item.status, jobOf(item.requestId)?.status)}`}
                >
                  {progressLabel(item.status, jobOf(item.requestId)?.status)}
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
