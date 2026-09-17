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
      /*
        확정을 막는 이유를 이 자리에서 말한다. 예전에는 확정 칸에 같은 말이 또 있었는데,
        정작 담는 자리는 여기라 읽는 사람이 화면 아래위를 오갔다.
      */
      <EmptyState
        title="소재를 투입해주세요"
        description="투입한 소재가 없어 생산을 확정할 수 없습니다."
      />
    )
  }

  const productUsed = sumOf(batch.inputs, (input) => input.productUsedQuantity)

  return (
    <section className="input-list">
      <table className="input-list__table">
        {/*
          제목은 위의 "투입된 소재" 가 이미 말한다 — 화면에 두 번 적지 않는다. 지우지 않고
          감추는 이유는 표를 소리로 읽는 사람에게는 그 이름이 유일한 안내여서다.
        */}
        <caption className="ui-visually-hidden">투입 소재와 사용량</caption>
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
              </th>
              <td>{formatKg(input.inputQuantity)}</td>
              <td>{formatKg(input.productUsedQuantity)}</td>
              <td>{formatKg(input.reusableScrapQuantity)}</td>
              {/* 사유는 폐기 수량 바로 아래다 — 그 숫자를 설명하는 말이라 붙어 있어야 한다. */}
              <td>
                {formatKg(input.wasteQuantity)}
                {input.wasteReason && (
                  <span className="input-list__reason">{input.wasteReason}</span>
                )}
              </td>
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

      {/*
        되돌릴 수 있는 구간이 여기까지라는 경고다 — 확정 버튼 바로 위라, 눈이 마지막으로
        닿는 오른쪽 끝에 붉게 둔다.
      */}
      {editable ? (
        <p className="input-list__warn-note">
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
