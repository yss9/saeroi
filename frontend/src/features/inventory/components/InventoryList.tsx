import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { MaterialProcessingBadge } from '../../material/components/MaterialProcessingBadge.tsx'
import { MATERIAL_TYPE_LABEL, type MaterialGenerationType } from '../../material/types/material.types.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../hooks/useMyMaterials.ts'
import { useMaterialLockSources, type LockSource } from '../hooks/useMaterialLockSources.ts'
import type { InventoryItem } from '../types/inventory.types.ts'

/** 보유 상태 필터. 서버에 상태 파라미터가 없어 받아온 배열을 여기서 거른다. */
type Filter = 'all' | 'active' | 'depleted'

const FILTERS: readonly { label: string; key: Filter }[] = [
  { label: '전체', key: 'all' },
  { label: '보유 중', key: 'active' },
  { label: '소진', key: 'depleted' },
]

/**
 * 보유 상태 배지 문구.
 *
 * <b>세 가지가 서로 다른 사실이다.</b>
 *
 *   소진      — 실제로 다 썼다. 되찾을 수 없다 (`isDepleted`, 보유량으로 판정)
 *   전량 잠김 — 갖고는 있는데 전부 묶여 있다. 잠금이 풀리면 돌아온다
 *   보유 중   — 지금 쓸 수 있는 양이 남아 있다
 *
 * 가운데가 빠져 있었다. 판매글이나 가공 요청에 걸면 잠기기만 하고 보유량은 그대로라
 * `status` 는 계속 `ACTIVE` 인데, 쓸 수 있는 양은 0 이다. 그대로 나란히 두면
 * <b>"0.0 kg · 보유 중"</b> 이 되어 사용자에게는 모순으로 읽힌다.
 *
 * <b>순서가 중요하다.</b> 소진을 먼저 본다 — 다 쓴 소재는 `available` 도 0 이라
 * 잠김을 먼저 물으면 되찾을 수 없는 소재를 "잠김" 이라고 적는다.
 */
function statusLabel(item: InventoryItem): { text: string; tone: 'ok' | 'wait' | 'flat' } {
  if (isDepleted(item)) return { text: '소진', tone: 'flat' }
  if (item.available <= 0) return { text: '전량 잠김', tone: 'wait' }
  return { text: '보유 중', tone: 'ok' }
}

/**
 * 내 보유 소재.
 *
 * <b>카드가 아니라 표다.</b> 행마다 똑같은 필드가 반복되는 목록이라, 카드로 늘어놓으면
 * 같은 값을 세로로 훑게 되어 수량 비교가 안 된다. 이 화면에 오는 이유는 "지금 쓸 수
 * 있는 것이 얼마인가" 이고, 그 답은 여러 소재를 <b>견줘야</b> 나온다.
 *
 * <b>세 역할이 같은 화면을 쓴다.</b> 공급업체·브랜드·가공업체가 각자 자기 보유분만
 * 보고(서버가 JWT `sub` 로 판단한다), 하는 일이 같아 화면을 가르지 않는다. 역할로
 * 갈리는 것은 「소재 등록」 버튼 하나뿐이다.
 *
 * 서버가 목록을 페이지로 나누지 않는다 (`getMyMaterials()` 가 배열을 통째로 준다).
 * 그래서 쪽 나눔이 없다 — 100건을 넘기기 시작하면 그때는 서버 쪽 일이다.
 */
export function InventoryList() {
  const { user, role } = useAuth()
  const navigate = useNavigate()
  const { data, isPending, isError, error, refetch } = useMyMaterials()
  const locks = useMaterialLockSources(user?.id)
  const [filter, setFilter] = useState<Filter>('all')

  if (isPending) return <Loading variant="skeleton" rows={4} label="보유 소재를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  // 소재 등록은 공급업체만 할 수 있다 (`/supplier/materials/new` 가 SUPPLIER 전용이다).
  // 브랜드·가공업체에게 이 링크를 내면 눌렀을 때 /403 으로 튕긴다 — 빈 화면에서 유일한
  // 출구가 막다른 길이 되는 셈이다.
  const canRegister = role === 'SUPPLIER'

  if (data.length === 0) {
    return (
      <EmptyState
        title="보유한 소재가 없습니다"
        description={
          canRegister
            ? '소재를 등록하면 보유 수량을 여기에서 확인할 수 있습니다.'
            : '소재를 사들이거나 가공을 마치면 보유 수량이 여기 표시됩니다.'
        }
        {...(canRegister
          ? { action: <Link to="/supplier/materials/new">소재 등록하기</Link> }
          : {})}
      />
    )
  }

  // 필터도 배지와 같은 잣대로 고른다. 서버 `status` 로 거르면 "소진" 을 눌러도
  // 0g 짜리가 안 나온다 — 서버가 아직 DEPLETED 로 넘기지 않기 때문이다.
  const filtered =
    filter === 'all' ? data : data.filter((item) => (filter === 'depleted' ? isDepleted(item) : !isDepleted(item)))

  // 소진된 것은 맨 아래로. 목록에서 찾는 것은 지금 쓸 수 있는 소재다.
  const rows = [...filtered].sort((a, b) => Number(isDepleted(a)) - Number(isDepleted(b)))

  const usable = data.reduce((sum, item) => sum + Math.max(0, item.available), 0)
  const locked = data.reduce((sum, item) => sum + item.lockedQuantity, 0)

  return (
    <>
      <header className="console__head">
        <div>
          {/* 사이드바가 이미 "보유 소재" 를 켜고 있다. 큰 제목은 같은 말을 두 번 하는
              것이라 감추되 DOM 에는 남긴다 — InventoryPage 가 h1 을 갖고 있다. */}
          <p className="console__sub">
            소재 {data.length}종 · 쓸 수 있는 양 <b className="console__num">{formatKg(usable)}</b>
            {locked > 0 && <> · 잠김 <b className="console__num">{formatKg(locked)}</b></>}
          </p>
        </div>
        {canRegister && (
          <div className="console__head-actions">
            <Link to="/supplier/materials/new" className="console-btn console-btn--primary">
              소재 등록
            </Link>
          </div>
        )}
      </header>

      <div className="console-tabs" role="group" aria-label="보유 상태">
        {FILTERS.map((item) => (
          <button
            key={item.key}
            type="button"
            className="console-tab"
            aria-pressed={item.key === filter}
            onClick={() => setFilter(item.key)}
          >
            {item.label}
            <span className="console-tab__count">{countFor(item.key, data)}</span>
          </button>
        ))}
      </div>

      {rows.length === 0 ? (
        <EmptyState title="조건에 맞는 소재가 없습니다" />
      ) : (
        <div className="console-table-wrap">
          <table className="console-table inv-table">
            <thead>
              <tr>
                <th scope="col">소재</th>
                <th scope="col">종류 · 등급</th>
                <th scope="col" className="is-num">쓸 수 있는 양</th>
                <th scope="col">보유 · 잠김</th>
                <th scope="col">잠긴 곳</th>
                <th scope="col">상태</th>
                <th scope="col">기준</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <InventoryRow
                  key={item.materialId}
                  item={item}
                  sources={locks.byMaterial.get(item.materialId) ?? []}
                  onOpen={() => navigate(`/materials/${item.materialId}`)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function InventoryRow({
  item,
  sources,
  onOpen,
}: {
  item: InventoryItem
  sources: LockSource[]
  onOpen: () => void
}) {
  const depleted = isDepleted(item)
  const status = statusLabel(item)
  const typeLabel =
    MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ?? item.materialType

  return (
    <tr className={`console-row${depleted ? ' console-row--off' : ''}`} onClick={onOpen}>
      {/* 행 전체가 눌리지만 이름은 링크로 둔다 — 행 클릭만으로는 키보드로 갈 수 없고,
          보조 기술에도 "어디로 가는 줄" 인지가 드러나지 않는다. */}
      <td className="console-row__name">
        <Link to={`/materials/${item.materialId}`} onClick={(event) => event.stopPropagation()}>
          {item.name}
        </Link>
        {/* 같은 이름의 소재가 여러 건 있을 수 있다 — 가공을 거쳐도 이름은 그대로다. 사람이
            가르는 값은 가공 이력이고, 기계가 가르는 값은 코드다. */}
        <MaterialProcessingBadge completedProcessTypes={item.completedProcessTypes} generationType={item.generationType as MaterialGenerationType}/>
        <small className="inv-code">{item.materialCode}</small>
      </td>

      {/* 칩 두 개를 나란히 놓는 상자를 칸 <b>안에</b> 둔다. td 자체를 flex 로 만들면 그
          칸이 표 레이아웃에서 빠져나와 열 정렬과 아래 선이 어긋난다. */}
      <td>
        <div className="inv-chips">
          <span className="console-chip">{typeLabel}</span>
          {/* 등급이 없는 소재가 흔하다. 빈 칩을 두면 없는 정보가 있는 것처럼 보인다. */}
          {item.grade && <span className="console-chip">{item.grade}등급</span>}
        </div>
      </td>

      {/* 이 표를 보는 이유가 되는 숫자. 음수가 와도 0으로 깎지 않는다 — 잠금 합계가
          보유량을 넘었다는 데이터 파손 신호이고, 숨기면 원인을 못 찾는다. */}
      <td className="is-num inv-usable">{formatKg(item.available)}</td>

      <td className="inv-split">
        <div>
          <span className="console__num">{formatKg(item.quantity)}</span>
          <span className="inv-split__lock">
            {item.lockedQuantity > 0 ? `${formatKg(item.lockedQuantity)} 잠김` : '잠김 없음'}
          </span>
        </div>
        {/* 보유량 대비 쓸 수 있는 비율. 숫자 둘을 견주는 것보다 빨리 읽힌다. */}
        <div
          className="console-bar inv-bar"
          role="img"
          aria-label={`보유 ${formatKg(item.quantity)} 중 ${formatKg(item.lockedQuantity)} 잠김`}
        >
          <span style={{ width: `${ratio(item)}%` }} />
        </div>
      </td>

      {/*
        잠긴 곳. <b>비는 칸이 많은 것이 정상이다</b> — 판매글 잠금만 되짚을 수 있고
        가공·생산 잠금은 조회 API 가 없다 (`useMaterialLockSources`). 조용한 「—」로
        두고 경고색을 쓰지 않는다.
      */}
      <td>
        {/* 칩 칸과 같은 이유로 상자를 안에 둔다 — td 에 flex 를 걸면 칸이 표에서 빠져나와
            칸 경계선이 행 사이로 삐져나온다. */}
        <div className="inv-lock-src">
          {sources.length === 0 ? (
            <span aria-hidden="true">—</span>
          ) : (
            sources.map((source) => (
              <Link
                key={source.listingId}
                to={`/marketplace/${source.listingId}`}
                onClick={(event) => event.stopPropagation()}
              >
                판매글 #{source.listingId}
              </Link>
            ))
          )}
        </div>
      </td>

      <td>
        <span className={`console-badge console-badge--${status.tone}`}>
          {status.tone === 'ok' && <span className="console-badge__dot" aria-hidden="true" />}
          {status.text}
        </span>
      </td>

      {/*
        이 숫자가 언제 기준인지. 온체인 잔액 동기화가 아직 없어 화면의 값이 방금 것인지
        어제 것인지 알 방법이 없었다 — 서버는 내려주고 있었는데 화면이 버렸다.
      */}
      <td className="inv-synced">{shortDateTime(item.syncedAt)}</td>
    </tr>
  )
}

function countFor(key: Filter, data: InventoryItem[]): number {
  if (key === 'all') return data.length
  return data.filter((item) => (key === 'depleted' ? isDepleted(item) : !isDepleted(item))).length
}

/** 막대 길이. 보유량이 0 이면 나눗셈이 성립하지 않으므로 빈 막대로 둔다. */
function ratio(item: InventoryItem): number {
  if (item.quantity <= 0) return 0
  return Math.min(100, Math.max(0, (item.available / item.quantity) * 100))
}

function shortDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 다 쓴 소재인가.
 *
 * **보유량으로 판정한다.** 서버의 `status` 는 아직 `DEPLETED` 로 넘어가지 않는다 —
 * 전환 메서드가 없어(`MaterialStatus` 주석) 0g 이 된 소재도 `ACTIVE` 로 온다. 그 값을
 * 그대로 적으면 0kg 짜리 소재가 "보유 중" 으로 보인다.
 *
 * **`available` 이 아니라 `quantity` 를 본다.** 판매글이나 가공에 전량이 잠긴 소재는
 * `available` 이 0 이지만 없어진 것이 아니다 — 잠금이 풀리면 돌아온다. 소진이라고 적으면
 * 되찾을 수 있는 소재를 잃은 것으로 읽는다.
 *
 * 서버가 `DEPLETED` 를 보내기 시작해도 그 판정을 존중한다.
 */
function isDepleted(item: InventoryItem): boolean {
  return item.status === 'DEPLETED' || item.quantity <= 0
}
