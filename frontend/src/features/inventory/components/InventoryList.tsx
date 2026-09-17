import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../hooks/useMyMaterials.ts'
import type { BalanceStatus, InventoryItem } from '../types/inventory.types.ts'

/**
 * 보유 상태 필터.
 *
 * 서버에 상태별로 필터링해 달라고 요청하는 파라미터가 없다 (`getMyMaterials()` 는
 * 전체를 받아온다) — 여기서는 이미 받아온 배열을 클라이언트에서만 거른다.
 */
const FILTERS: readonly { label: string; status?: BalanceStatus }[] = [
  { label: '전체' },
  { label: '보유 중', status: 'ACTIVE' },
  { label: '소진', status: 'DEPLETED' },
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
function statusLabel(item: InventoryItem): { text: string; tone: 'success' | 'neutral' | 'warning' } {
  if (isDepleted(item)) return { text: '소진', tone: 'neutral' }
  if (item.available <= 0) return { text: '전량 잠김', tone: 'warning' }
  return { text: '보유 중', tone: 'success' }
}

/**
 * 내 보유 소재 카드 목록.
 *
 * <b>수량을 세 층으로 보여준다.</b> 예전에는 `available` 하나만 있어서, 그 숫자가 왜
 * 그런지 알 방법이 없었다 — 30kg 을 갖고 있는데 "판매 가능 12kg" 만 보이면 나머지 18kg 이
 * 어디 갔는지 화면 어디에도 없다. 응답에는 보유량과 잠긴 양이 함께 오는데 쓰지 않고 있었다.
 *
 * <b>잠긴 이유는 아직 못 보여준다.</b> `GET /materials/{id}/locks` 가 없어서 "판매글 2건에
 * 잠김" 같은 내역을 만들 수 없다. 숫자까지만 정직하게 보여주고, 사유는 그 API 가 생기면
 * 붙인다 — 지금 지어내면 틀린 말이 화면에 남는다.
 */
export function InventoryList() {
  const { data, isPending, isError, error, refetch } = useMyMaterials()
  // 상태 필터는 이 컴포넌트 안에서만 쓰는 값이라 useState 다.
  const [status, setStatus] = useState<BalanceStatus | undefined>(undefined)

  if (isPending) return <Loading variant="skeleton" rows={4} label="보유 소재를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.length === 0) {
    return (
      <EmptyState
        title="보유한 소재가 없습니다"
        description="소재를 등록하면 Material Token이 발행되고 보유 수량이 여기 표시됩니다."
        action={<Link to="/supplier/materials/new">소재 등록하기</Link>}
      />
    )
  }

  // 필터도 같은 잣대로 고른다. 서버 status 로 거르면 "소진" 을 눌러도 0g 짜리가 안 나온다.
  const filtered = status
    ? data.filter((item) => (status === 'DEPLETED' ? isDepleted(item) : !isDepleted(item)))
    : data

  return (
    <>
      <div className="inventory-filters" role="group" aria-label="보유 상태 필터">
        {FILTERS.map((filter) => {
          const selected = filter.status === status
          return (
            <Button
              key={filter.label}
              size="sm"
              variant={selected ? 'primary' : 'secondary'}
              aria-pressed={selected}
              onClick={() => setStatus(filter.status)}
            >
              {filter.label}
            </Button>
          )
        })}
      </div>

      {filtered.length === 0 ? (
        <EmptyState title="조건에 맞는 소재가 없습니다" />
      ) : (
        <ul className="inventory-grid">
          {filtered.map((item) => (
            <li
              key={item.materialId}
              // 소진된 소재는 뒤로 물린다. 목록에서 찾는 것은 지금 쓸 수 있는 소재다.
              className={`inventory-card${isDepleted(item) ? ' inventory-card--used-up' : ''}`}
            >
              <Link to={`/materials/${item.materialId}`} className="inventory-card__link">
                {/* 상태는 맨 위 왼쪽. 카드 아래에 있으면 여러 장을 훑을 때마다 시선이
                    끝까지 내려갔다 올라와야 한다. */}
                <div className="inventory-card__status">
                  <StatusBadge tone={statusLabel(item).tone}>
                    {statusLabel(item).text}
                  </StatusBadge>
                </div>
                <strong className="inventory-card__name">{item.name}</strong>
                {/*
                  소재 코드는 적지 않는다. 카드에서 고르는 기준은 이름이고, 코드가
                  이름 바로 아래 붙어 있으면 이름보다 먼저 눈에 걸린다. 상세에는 있다.
                */}
                <span className="inventory-card__type">
                  {MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ??
                    item.materialType}
                </span>
                <div className="inventory-card__figures">
                  {/* 이 카드를 보는 이유가 되는 숫자. 음수가 와도 0으로 깎지 않는다 —
                      잠금 합계가 보유량을 넘었다는 데이터 파손 신호이고, 숨기면 원인을 못 찾는다. */}
                  <div className="inventory-card__lead">
                    <span>쓸 수 있는 양</span>
                    <strong className="inventory-card__available">{formatKg(item.available)}</strong>
                  </div>

                  {/*
                    잠긴 양이 있을 때만 쪼개서 보여준다. 0 이면 "보유 = 가능" 이라
                    같은 숫자를 두 번 쓰는 셈이고, 카드마다 줄 수가 달라지는 대신
                    읽을 것이 줄어든다.
                  */}
                  {item.lockedQuantity > 0 && (
                    <>
                      <div
                        className="inventory-card__bar"
                        role="img"
                        aria-label={`보유 ${formatKg(item.quantity)} 중 ${formatKg(item.lockedQuantity)} 잠김`}
                      >
                        <span
                          style={{
                            width: `${Math.min(100, Math.max(0, (item.available / item.quantity) * 100))}%`,
                          }}
                        />
                      </div>
                      <p className="inventory-card__split">
                        <span>보유 {formatKg(item.quantity)}</span>
                        <span>잠김 {formatKg(item.lockedQuantity)}</span>
                      </p>
                    </>
                  )}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
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
