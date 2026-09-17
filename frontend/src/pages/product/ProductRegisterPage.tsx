import { useState } from 'react'
import { ErrorMessage, Modal } from '../../components/common/index.ts'
import { ProductionBatchChooser } from '../../features/production/components/ProductionBatchChooser.tsx'
import { useAssignProductModel } from '../../features/production/hooks/useAssignProductModel.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import {
  ProductCreatedSummary,
  ProductForm,
} from '../../features/product/components/ProductForm.tsx'
import type { ProductModel } from '../../features/product/types/product.types.ts'

/**
 * 제품 등록 (Brand).
 *
 * **배치 목록이 입구다.** 제품 등록만으로는 팔 물건이 없고 개체를 만드는 것은 생산
 * 배치다 — 배치를 고르고 "이걸로 무엇을 만들었나" 를 적는 순서가 실제 일의 순서다.
 *
 * 폼은 팝업으로 연다. 목록과 폼을 한 화면에 쌓으면 배치를 고르고도 어느 배치에 적는지가
 * 위로 밀려 올라가 버린다.
 *
 * 연결은 등록이 **끝난 뒤에야** 할 수 있다. 배치에 적어 넣을 제품 번호가 그때 생기기
 * 때문이다 — 그래서 고르기만 해 두었다가 등록 응답을 받는 즉시 잇는다.
 *
 * 두 기능(product · production)을 이 화면이 조합한다. 기능끼리는 서로를 모른다.
 */
export function ProductRegisterPage() {
  // 지금 제품을 적고 있는 배치. `null` 이면 배치 없이 등록하는 중이다.
  const [batch, setBatch] = useState<ProductionBatch | null>(null)
  const [writing, setWriting] = useState(false)
  // 방금 등록한 제품. 창을 닫은 뒤에도 결과가 목록 위에 남는다.
  const [created, setCreated] = useState<ProductModel | null>(null)
  const assign = useAssignProductModel()

  function open(next: ProductionBatch | null) {
    setBatch(next)
    setCreated(null)
    assign.reset()
    setWriting(true)
  }

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 등록</h1>

      {created && <ProductCreatedSummary product={created} />}

      {/*
        연결 실패는 등록 실패가 아니다. 제품은 이미 저장됐고 배치만 비어 있으므로, 사실만
        알린다 — 배치는 나중에 다시 이을 수 있다.
      */}
      {assign.isError && <ErrorMessage error={assign.error} />}
      {assign.isSuccess && (
        <p className="page__lead" role="status">
          {assign.data.batchName} 생산에 연결했습니다. 그 생산을 확정하면 제품 개체와 QR 이
          만들어집니다.
        </p>
      )}

      <ProductionBatchChooser onChoose={open} onSkip={() => open(null)} />

      <Modal
        open={writing}
        title={batch === null ? '제품 등록' : `${batch.batchName} · 제품 등록`}
        onClose={() => setWriting(false)}
      >
        <ProductForm
          onCreated={(product) => {
            setCreated(product)
            setWriting(false)
            if (batch === null) return
            assign.mutate({
              batchId: batch.productionBatchId,
              productModelId: product.productModelId,
            })
          }}
        />
      </Modal>
    </main>
  )
}
