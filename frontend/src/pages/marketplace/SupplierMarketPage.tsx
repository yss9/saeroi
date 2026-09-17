import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CaretUp, CheckCircle, EyeSlash, Plus, Storefront, WarningCircle } from '@phosphor-icons/react'
import { ConfirmModal, ErrorMessage, Loading, Pagination } from '../../components/common/index.ts'
import { ListingForm } from '../../features/marketplace/components/ListingForm.tsx'
import { MaterialSelect } from '../../features/marketplace/components/MaterialSelect.tsx'
import { useCancelListing } from '../../features/marketplace/hooks/useCancelListing.ts'
import { useListingChainAction } from '../../features/marketplace/hooks/useListingChainAction.ts'
import { useListings } from '../../features/marketplace/hooks/useListings.ts'
import { ListingChainCancelButton } from '../../features/marketplace/components/ListingChainCancelButton.tsx'
import { SupplierListingCard } from '../../features/supplier/components/SupplierListingCard.tsx'
import { useSupplierListingSummary } from '../../features/supplier/hooks/useSupplierListingSummary.ts'
import { useMyMaterials } from '../../features/inventory/hooks/useMyMaterials.ts'
import { formatKg } from '../../utils/quantity.ts'
import type { Listing, ListingStatus } from '../../features/marketplace/types/marketplace.types.ts'
import type { InventoryItem } from '../../features/inventory/types/inventory.types.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import '../../components/common/console.css'

/** 목록 위의 상태 탭. 서버가 기본으로 ON_SALE 만 주므로 "전체" 는 상태를 비워 보낸다. */
const TABS: readonly { label: string; status?: ListingStatus }[] = [
  { label: '판매 중', status: 'ON_SALE' },
  { label: '품절', status: 'SOLD_OUT' },
  { label: '내림', status: 'CANCELED' },
]

/**
 * Supplier Market — 소재를 파는 쪽.
 *
 * <b>목록이 먼저다.</b> 공급업체가 이 화면에 오는 이유는 대부분 "내 판매글이 어떻게 됐나"
 * 이고, 등록은 가끔 하는 일이다. 예전에는 등록 폼이 통째로 위에 펼쳐져 있어 — 소재를
 * 고르기 전에는 안내 박스가 그 자리를 차지한 채 — 목록이 화면 아래로 밀렸다.
 *
 * 그래서 등록은 접어 두고 버튼으로 연다. 다만 <b>소재 상세에서 "판매 등록" 을 눌러 온
 * 경우</b>(`?materialId=`)는 등록하러 온 것이 분명하므로 열린 채로 시작한다.
 *
 * <b>목록을 이 화면이 직접 그린다</b> (`ListingList` 를 쓰지 않는다). 판매자에게 필요한
 * 것이 구매자와 다르기 때문이다 — 소진율과 이력 기록 여부는 여기서만 보여주고, 그 둘을
 * 공유 컴포넌트에 밀어 넣으면 브랜드 화면에 필요 없는 정보가 늘어난다.
 */
export function SupplierMarketPage() {
  const [cancelTarget, setCancelTarget] = useState<Listing | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<ListingStatus | undefined>('ON_SALE')
  const [selected, setSelected] = useState<InventoryItem | null>(null)
  const [isRegisterOpen, setRegisterOpen] = useState(false)
  const { mutate: cancel, isPending: isCanceling } = useCancelListing()
  const chain = useListingChainAction()

  // 내 판매글만 본다. 서버가 이 필터를 강제하지 않는 것은 의도적이다 — 판매자별 보기는
  // 권한이 아니라 필터이고, 어차피 전체 목록에 다 나와 있는 정보다.
  const sellerId = useAuthStore((state) => state.user?.id)

  const summary = useSupplierListingSummary(sellerId)
  const listings = useListings({ sellerId, status, page })

  // 소재 상세("보유 소재 팔기")에서 ?materialId= 로 넘어온 경우 그 소재를 미리 골라 둔다.
  //
  // useEffect 로 하지 않는다. 데이터를 늦게 받아와 반영하는 것은 "렌더 중 상태 조정"이지
  // 외부 시스템 동기화가 아니다 — 이펙트에서 setState를 부르면 불필요한 리렌더가
  // 한 번 더 낀다 (react-hooks/set-state-in-effect).
  const [searchParams] = useSearchParams()
  const initialMaterialId = Number(searchParams.get('materialId'))
  const { data: myMaterials } = useMyMaterials()
  const [appliedMaterialId, setAppliedMaterialId] = useState<number | null>(null)

  if (
    Number.isInteger(initialMaterialId) &&
    initialMaterialId > 0 &&
    myMaterials !== undefined &&
    appliedMaterialId !== initialMaterialId
  ) {
    setAppliedMaterialId(initialMaterialId)
    const match = myMaterials.find((item) => item.materialId === initialMaterialId)
    if (match) setSelected(match)
    // 소재를 들고 왔다 = 팔러 온 것이다. 접힌 채로 두면 버튼을 한 번 더 눌러야 한다.
    setRegisterOpen(true)
  }

  function changeTab(next: ListingStatus | undefined) {
    setStatus(next)
    // 3페이지를 보다가 탭을 바꾸면 결과가 한 페이지로 줄어들 수 있고,
    // 그러면 3페이지는 빈 화면이라 "결과가 없다" 로 읽힌다.
    setPage(0)
  }

  return (
    <main className="console">
      <header className="console__head">
        <div>
          {/*
            <b>제목은 눈에 보이지 않는다.</b> 사이드바가 이미 "판매 관리" 를 켠 채로
            띄우고 있어, 큰 제목을 또 그리면 같은 말이 두 번이다(7511d10 이 앱 전체에
            적용한 규칙). DOM 에는 남겨 둔다 — 사이드바가 접히는 좁은 화면과 보조 기술
            에게는 이것이 유일한 화면 이름이다.
          */}
          <h1 className="ui-visually-hidden">판매 관리</h1>
          {/* 건수는 바로 아래 지표가 이미 말한다. 여기서는 지표에 없는 것 하나만 —
              지금 팔려고 걸어 둔 양이 얼마인지. */}
          <p className="console__sub">
            지금 팔고 있는 수량{' '}
            <b className="console__num">
              {summary.isPending ? '—' : formatKg(summary.availableGram)}
            </b>
          </p>
        </div>

        <div className="console__head-actions">
          <button
            type="button"
            className={`console-btn ${isRegisterOpen ? 'console-btn--ghost' : 'console-btn--primary'}`}
            onClick={() => setRegisterOpen((open) => !open)}
            aria-expanded={isRegisterOpen}
            aria-controls="console-register"
          >
            {isRegisterOpen ? (
              <>
                <CaretUp aria-hidden="true" /> 등록 닫기
              </>
            ) : (
              <>
                <Plus aria-hidden="true" weight="bold" /> 판매 등록
              </>
            )}
          </button>
        </div>
      </header>

      {/*
        요약 지표.

        마지막 칸만 색을 쓴다 — 앞의 셋은 "지금 이런 상태다" 이고 마지막만 "네가 할 일이
        남았다" 이기 때문이다. 넷 다 색을 주면 어느 것이 급한지 사라진다.
      */}
      <div className="console-metrics">
        <Metric label="판매 중" value={summary.onSale} unit="건" lead pending={summary.isPending} icon={<Storefront weight="fill" />} />
        <Metric label="품절" value={summary.soldOut} unit="건" pending={summary.isPending} icon={<CheckCircle weight="fill" />} />
        <Metric label="내림" value={summary.canceled} unit="건" pending={summary.isPending} icon={<EyeSlash weight="fill" />} />
        <Metric
          label={summary.unrecorded > 0 ? '이력 미기록 · 서명 필요' : '이력 미기록'}
          value={summary.unrecorded}
          unit="건"
          todo={summary.unrecorded > 0}
          pending={summary.isPending}
          icon={<WarningCircle weight="fill" />}
        />
      </div>

      {isRegisterOpen && (
        <section id="console-register" className="console-panel console-register">
          <div className="console__title console-register__title">
            <strong>새 판매글 등록</strong>
            <span className="console__tag">write</span>
          </div>

          <MaterialSelect value={selected?.materialId ?? null} onChange={setSelected} />

          {/* 소재를 고르기 전에는 폼을 띄우지 않는다. 수량 입력의 최대값이 고른 소재의
              가용량이라, 소재 없이 먼저 입력하게 하면 그 값을 검증할 기준이 없다. */}
          {selected === null ? (
            <p className="console-register__hint">소재를 고르면 수량과 가격을 입력할 수 있습니다.</p>
          ) : (
            <ListingForm
              // key 로 소재를 넣어 소재를 바꾸면 폼이 새로 마운트된다. 그러지 않으면
              // 이전 소재에 맞춰 입력한 수량이 남아 새 소재의 가용량을 넘길 수 있다.
              key={selected.materialId}
              materialId={selected.materialId}
              materialName={selected.name}
              availableQuantity={selected.available}
              // 등록이 끝나면 폼을 접는다. 방금 올린 글이 아래 목록에 보여야 하는데,
              // 펼쳐진 폼이 그 자리를 가린다.
              onCreated={() => {
                setSelected(null)
                setRegisterOpen(false)
                changeTab('ON_SALE')
              }}
            />
          )}
        </section>
      )}

      <div className="console-tabs" role="group" aria-label="판매글 상태">
        <button
          type="button"
          className="console-tab"
          aria-pressed={status === undefined}
          onClick={() => changeTab(undefined)}
        >
          전체
          <span className="console-tab__count">{countFor(undefined, summary)}</span>
        </button>
        {TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            className={`console-tab ${tab.status === 'ON_SALE' && summary.unrecorded > 0 ? 'console-tab--todo' : ''}`}
            aria-pressed={tab.status === status}
            onClick={() => changeTab(tab.status)}
          >
            {tab.label}
            <span className="console-tab__count">{countFor(tab.status, summary)}</span>
          </button>
        ))}
        {listings.data && (
          <span className="console-tabs__right">
            {listings.data.totalElements}건 중 {listings.data.content.length}건 표시
          </span>
        )}
      </div>

      {listings.isPending ? (
        <Loading variant="skeleton" rows={3} label="판매글을 불러오는 중" />
      ) : listings.isError ? (
        <ErrorMessage error={listings.error} onRetry={() => void listings.refetch()} />
      ) : listings.data.content.length === 0 ? (
        <div className="console-empty">
          <h3>등록한 판매글이 없습니다</h3>
          <p>보유 소재를 판매 등록하면 여기에 표시됩니다.</p>
          <button type="button" className="console-btn console-btn--primary" onClick={() => setRegisterOpen(true)}>
            <Plus aria-hidden="true" weight="bold" /> 판매 등록
          </button>
        </div>
      ) : (
        <ul className="console-grid">
          {listings.data.content.map((listing) => (
            <SupplierListingCard
              key={listing.listingId}
              listing={listing}
              onCancel={setCancelTarget}
              // 이미 내린 글에는 취소 버튼 대신, 체인에 잠금이 남아 있으면 그것을 푸는
              // 자리를 준다. 서명을 놓친 글은 이 자리가 없으면 영영 정리되지 않고,
              // 그 소재는 다시 팔 수 없다.
              extraAction={
                listing.status === 'CANCELED' ? <ListingChainCancelButton listing={listing} /> : undefined
              }
            />
          ))}
        </ul>
      )}

      {listings.data && listings.data.totalPages > 1 && (
        <div className="console-foot">
          <Pagination page={listings.data} onChange={setPage} />
        </div>
      )}

      {/* 취소는 되돌릴 수 없다. 수정 기능이 없어 재등록해야 하므로 한 번 더 묻는다. */}
      <ConfirmModal
        open={cancelTarget !== null}
        title="판매를 취소할까요?"
        message="취소하면 남은 수량의 판매가 종료되고 다시 등록해야 합니다. 이미 진행 중인 거래는 영향을 받지 않습니다."
        confirmLabel="판매 취소"
        destructive
        loading={isCanceling || chain.isBusy}
        onConfirm={() => {
          if (cancelTarget === null) return
          const chainListingId = cancelTarget.chainListingId
          // 취소 사유를 보내지 않는다 — material_listing 에 사유 컬럼이 없다.
          cancel(cancelTarget.listingId, {
            // **서버 취소가 끝나면 체인의 잠금도 풀어야 한다.**
            //
            // 판매 등록은 체인에서 소재 잔액을 잠갔고, 그것을 푸는 것은 cancelListing
            // 뿐이다. 여기서 놓치면 DB 는 풀렸는데 체인은 잠긴 채라 같은 소재를 다시
            // 팔 수 없다 — Marketplace: insufficient available balance 로 막힌다.
            onSuccess: () => {
              if (chainListingId !== null) void chain.cancel(chainListingId)
            },
            onSettled: () => setCancelTarget(null),
          })
        }}
        onClose={() => setCancelTarget(null)}
      />
    </main>
  )
}

interface MetricProps {
  label: string
  value: number
  unit: string
  icon: React.ReactNode
  lead?: boolean
  todo?: boolean
  /** 집계가 아직 안 왔다. 0 을 그리면 "없다" 로 읽힌다 */
  pending?: boolean
}

function Metric({ label, value, unit, icon, lead, todo, pending }: MetricProps) {
  return (
    // 라벨과 숫자를 한 덩어리로 묶는다. "4" 만 읽히면 무엇이 4인지 알 수 없고,
    // 화면에서도 같은 낱말이 탭·배지에 또 있어 어느 것을 가리키는지 흐려진다.
    <div
      role="group"
      aria-label={label}
      className={['console-metric', lead ? 'console-metric--lead' : '', todo ? 'console-metric--todo' : '']
        .filter(Boolean)
        .join(' ')}
    >
      <div>
        <span className="console-metric__label">{label}</span>
        <span className="console-metric__value">
          {/* 아직 모르는 값을 0 으로 그리지 않는다. 0 은 "없다" 는 <b>주장</b>이라,
              도착하면 숫자가 튀고 그 사이 사용자는 틀린 사실을 읽는다. */}
          {pending ? '—' : value}
          <span className="console-metric__unit">{unit}</span>
        </span>
      </div>
      <span className="console-metric__icon" aria-hidden="true">
        {icon}
      </span>
    </div>
  )
}

function countFor(
  status: ListingStatus | undefined,
  summary: { onSale: number; soldOut: number; canceled: number },
): number {
  if (status === 'ON_SALE') return summary.onSale
  if (status === 'SOLD_OUT') return summary.soldOut
  if (status === 'CANCELED') return summary.canceled
  return summary.onSale + summary.soldOut + summary.canceled
}
