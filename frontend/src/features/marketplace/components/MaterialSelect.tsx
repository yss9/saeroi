import { processingHistoryText } from '../../material/utils/processingHistory.ts'
import type { MaterialGenerationType } from '../../material/types/material.types.ts'
import { EmptyState, ErrorMessage, Loading, Select } from '../../../components/common/index.ts'
import { useMyMaterials } from '../../inventory/hooks/useMyMaterials.ts'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { formatKg } from '../../../utils/quantity.ts'

export interface MaterialSelectProps {
  /** 선택된 소재 id. 아직 고르지 않았으면 null */
  value: number | null
  onChange: (material: InventoryItem | null) => void
}

/**
 * 판매할 소재를 고른다.
 *
 * **`available` 을 기준으로 다룬다. `quantity` 가 아니다.**
 * 보유량에는 이미 다른 판매글이나 가공 요청에 잠긴 몫이 섞여 있어서, 그걸 기준으로
 * 보여주면 사용자가 팔 수 없는 수량을 입력하고 서버가 409 로 거절한다. 실제로 그 일이
 * 있었다 — 고정값이 18kg 이라 표시하는데 실제 가용량은 10kg 이었다.
 *
 * **가용량이 0 인 소재는 고를 수 없게 막는다.** 목록에서 아예 빼지 않는 이유는,
 * 안 보이면 "내 소재가 사라졌다" 로 읽히기 때문이다. 보이되 왜 못 파는지 알려준다.
 *
 * 선택 결과로 id 만 주지 않고 **항목 전체**를 올려보낸다. 호출부가 이름과 가용량을
 * 다시 찾으려면 목록을 또 들고 있어야 하고, 그러면 두 곳이 어긋날 수 있다.
 */
export function MaterialSelect({ value, onChange }: MaterialSelectProps) {
  const { data, isPending, isError, error, refetch } = useMyMaterials()

  if (isPending) return <Loading label="보유 소재를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (data.length === 0) {
    return (
      <EmptyState
        title="보유한 소재가 없습니다"
        description="소재를 먼저 등록하면 마켓플레이스에 판매할 수 있습니다."
      />
    )
  }

  const sellable = data.filter((item) => item.available > 0)

  return (
    <>
      <Select
        label="판매할 소재"
        placeholder="소재를 선택하세요"
        value={value === null ? '' : String(value)}
        options={data.map((item) => ({
          value: String(item.materialId),
          label: optionLabel(item),
          // 잠금 때문에 팔 수 있는 양이 없는 소재. 고르면 수량 입력이 무의미하다.
          disabled: item.available <= 0,
        }))}
        onChange={(event) => {
          const selected = data.find(
            (item) => String(item.materialId) === event.target.value,
          )
          onChange(selected ?? null)
        }}
      />

      {/* 팔 수 있는 소재가 하나도 없으면 이유를 알려준다. 그러지 않으면 목록은 보이는데
          전부 선택이 안 되는 상태가 설명 없이 남는다. */}
      {sellable.length === 0 && (
        <p className="listing-form__note">
          보유한 소재가 모두 다른 판매글이나 가공 요청에 잠겨 있어 지금은 판매할 수 없습니다.
        </p>
      )}
    </>
  )
}

/**
 * 목록에 보일 문구.
 *
 * 가용량을 **이름과 함께** 넣는다. 선택 후에야 알 수 있게 하면 사용자가 목록을
 * 여러 번 열어보며 비교해야 한다.
 */
function optionLabel(item: InventoryItem): string {
  // 가공을 거쳐도 이름은 그대로라, 같은 이름끼리는 가공 이력으로 가른다.
  const name = `${item.name} · ${processingHistoryText(item.completedProcessTypes, item.generationType as MaterialGenerationType)}`
  if (item.available <= 0) {
    return `${name} · 판매 가능 수량 없음`
  }
  return `${name} · 판매 가능 ${formatKg(item.available)}`
}
