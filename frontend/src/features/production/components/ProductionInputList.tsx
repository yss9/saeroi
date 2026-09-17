import { Button, EmptyState, ErrorMessage } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../../inventory/hooks/useMyMaterials.ts'
import { useRemoveProductionInput } from '../hooks/useRemoveProductionInput.ts'
import type { ProductionBatch, ProductionInput } from '../types/production.types.ts'
import './production.css'

export interface ProductionInputListProps {
  batch: ProductionBatch
}

/**
 * 배치에 저장된 투입과 사용량.
 *
 * **저장하면 입력한 값이 화면에서 사라지는 문제를 메운다.** `MaterialInputSelect` 는 보내고
 * 초안을 비우므로, 그 뒤 무엇이 얼마나 들어갔는지 확인할 곳이 필요하다.
 *
 * **합계를 여기서 계산한다.** 배치의 `totalUsedQuantity` · `virginUsedQuantity` ·
 * `upcycledUsedQuantity` 는 **확정 전까지 전부 0** 이다 (`ProductionBatch.confirm()` 이 채운다).
 * 그 값을 그대로 보여주면 "투입했는데 합계가 0" 이 되어 화면이 거짓을 말한다.
 *
 * 취소에 확인 모달을 두지 않는다. 되돌릴 수 없는 동작이 아니다 — 잠금이 풀려 수량이
 * 돌아오고 같은 소재를 다시 넣을 수 있다. 매 줄에 모달을 붙이면 잘못 넣은 투입을 고치는
 * 일이 오히려 번거로워진다.
 */
export function ProductionInputList({ batch }: ProductionInputListProps) {
  const remove = useRemoveProductionInput(batch.productionBatchId)
  // 투입 응답에 소재 이름이 없어 보유 목록에서 찾아 붙인다 (아래 nameOf 참고).
  const materials = useMyMaterials()

  const editable = batch.status === 'DRAFT'

  if (batch.inputs.length === 0) {
    return (
      <EmptyState
        title="투입한 소재가 없습니다"
        description="위에서 보유 소재를 골라 사용량을 입력하면 여기에 표시됩니다."
      />
    )
  }

  const productUsed = sumOf(batch.inputs, (input) => input.productUsedQuantity)

  return (
    <section className="input-list">
      <table className="input-list__table">
        <caption className="input-list__caption">투입 소재와 사용량</caption>
        <thead>
          <tr>
            <th scope="col">소재</th>
            <th scope="col">투입</th>
            <th scope="col">제품 사용</th>
            <th scope="col">자투리</th>
            <th scope="col">폐기</th>
            {editable && <th scope="col">취소</th>}
          </tr>
        </thead>
        <tbody>
          {batch.inputs.map((input) => (
            <tr key={input.productionInputId}>
              <th scope="row" className="input-list__material">
                {nameOf(input, materials.data)}
                {/* 폐기 사유는 그 소재에 붙은 설명이라 같은 칸에 둔다. */}
                {input.wasteReason && (
                  <span className="input-list__reason">폐기 사유: {input.wasteReason}</span>
                )}
              </th>
              <td>{formatKg(input.inputQuantity)}</td>
              <td>{formatKg(input.productUsedQuantity)}</td>
              <td>{formatKg(input.reusableScrapQuantity)}</td>
              <td>{formatKg(input.wasteQuantity)}</td>
              {editable && (
                <td>
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={remove.isPending && remove.variables === input.productionInputId}
                    disabled={remove.isPending}
                    onClick={() => remove.mutate(input.productionInputId)}
                  >
                    취소
                  </Button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">합계</th>
            <td>{formatKg(sumOf(batch.inputs, (input) => input.inputQuantity))}</td>
            <td>{formatKg(productUsed)}</td>
            <td>{formatKg(sumOf(batch.inputs, (input) => input.reusableScrapQuantity))}</td>
            <td>{formatKg(sumOf(batch.inputs, (input) => input.wasteQuantity))}</td>
            {editable && <td />}
          </tr>
        </tfoot>
      </table>

      {remove.isError && <ErrorMessage error={remove.error} />}

      {/* 전량이 자투리·폐기면 확정이 거부된다 (PRODUCTION_NO_PRODUCT_MATERIAL) — 제품을
          만들지 못하기 때문이다. 확정 버튼을 눌러 보고 알게 되면 이유를 찾기 어렵다. */}
      {editable && productUsed === 0 && (
        <p className="input-list__warning" role="alert">
          제품에 쓰인 수량이 없습니다. 투입이 전부 자투리·폐기면 제품을 만들 수 없어 생산을
          확정할 수 없습니다.
        </p>
      )}

      {editable ? (
        <p className="input-list__note">
          투입을 취소하면 잠긴 수량이 보유 소재로 돌아옵니다. 확정한 뒤에는 취소할 수 없습니다.
        </p>
      ) : (
        <p className="input-list__note">확정된 생산입니다. 투입을 바꿀 수 없습니다.</p>
      )}
    </section>
  )
}

function sumOf(inputs: readonly ProductionInput[], pick: (input: ProductionInput) => number): number {
  return inputs.reduce((total, input) => total + pick(input), 0)
}

/**
 * 소재 이름.
 *
 * **투입 응답에는 `materialId` 만 있고 이름이 없다** (`ProductionInputResponse`). 보유 목록에서
 * 찾아 붙인다 — `DRAFT` 배치의 소재는 잠겨 있을 뿐 여전히 보유 중이라 목록에 있다.
 *
 * 못 찾으면 id 를 보여준다. 확정된 배치의 소재는 소비되어 보유 목록에서 사라지고, 그때는
 * "소재 #12" 라도 무엇을 가리키는지는 분명하다. 서버가 이름을 함께 내려주면 이 함수는
 * 사라진다 — Backend 에 요청해 둘 만한 값이다.
 */
function nameOf(input: ProductionInput, materials?: { materialId: number; name: string }[]): string {
  const found = materials?.find((material) => material.materialId === input.materialId)
  return found ? found.name : `소재 #${input.materialId}`
}
