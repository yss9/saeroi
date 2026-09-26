import { useRef, useState } from 'react'
import { useCompleteAction, useRejectAction } from '../hooks/useAgentConversation.ts'
import { StatusOrActions } from './PaymentCard.tsx'
import { uploadProductImage } from '../../product/api/uploadProductImage.ts'
import type { PendingAction } from '../types/agent.types.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { PRODUCT_CATEGORY_LABEL, type ProductCategory } from '../../product/types/product.types.ts'

/** 제품 등록만 이미지를 받는다. 다른 확인 카드에 업로드가 뜨면 담당자가 헷갈린다. */
const NEEDS_IMAGE = 'register_product_model'

/**
 * 단순 확인 카드.
 *
 * 지갑도 결제도 필요 없지만 <b>되돌리기 어려운 쓰기</b>라 사람이 한 번 봐야 하는 작업이다.
 * 수령 확정이 대표적이다 — 확정하는 순간 소유권이 넘어오고 판매자에게 돈이 간다. 담당자가
 * 물건을 실제로 받았는지는 서버가 알 수 없으므로 모델의 판단에 맡기지 않는다.
 *
 * <b>제품 등록은 여기서 이미지를 받는다.</b> 채팅으로는 파일을 올릴 수 없고, 모델이 이미지
 * 주소를 지어내면 마켓에 깨진 이미지가 올라간다. 그래서 주소를 모델의 인자에서 빼고 이
 * 카드가 업로드를 맡는다 — 올린 주소는 승인과 함께 `resultJson` 으로 서버에 전달된다.
 */
export function ConfirmCard({ action }: { action: PendingAction }) {
  const complete = useCompleteAction()
  const reject = useRejectAction()
  const [error, setError] = useState<string | null>(null)

  const needsImage = action.toolName === NEEDS_IMAGE
  const [imageKey, setImageKey] = useState<string | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  async function handlePick(file: File | undefined) {
    if (!file) return
    setError(null)
    setUploading(true)
    try {
      const uploaded = await uploadProductImage(file)
      setImageKey(uploaded.storageKey)
      // 미리보기는 업로드한 파일 자체로 만든다. 저장 주소는 서명이 필요할 수 있어
      // 그대로 <img src> 에 넣으면 보이지 않는 경우가 있다.
      setPreview(URL.createObjectURL(file))
    } catch {
      setError('이미지를 올리지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setUploading(false)
    }
  }

  async function handleConfirm() {
    setError(null)
    try {
      await complete.mutateAsync({
        actionId: action.id,
        resultJson: imageKey ? JSON.stringify({ thumbnailUrl: imageKey }) : undefined,
      })
    } catch {
      // 도메인이 거절한 경우(아직 발송 전 등)는 서버가 대화에 이유를 남긴다.
      setError('처리하지 못했습니다. 대화 내용을 확인해 주세요.')
    }
  }

  // 이미지가 필요한 카드는 올리기 전까지 승인할 수 없다. 서버도 막지만, 거기서 막히면
  // 담당자는 이미 누른 뒤이고 화면에는 읽기 어려운 검증 오류만 남는다.
  const disabled =
    action.status !== 'PENDING' || complete.isPending || uploading || (needsImage && !imageKey)

  return (
    <div className="agent-card agent-card--confirm">
      <p className="agent-card__title">확인이 필요합니다</p>
      <p className="agent-card__raw">{action.summary}</p>

      {needsImage && <ProductDetails payloadJson={action.payloadJson} />}
      {action.toolName === 'add_production_inputs' && (
        <ProductionInputDetails payloadJson={action.payloadJson} />
      )}

      {needsImage && action.status === 'PENDING' && (
        <div className="agent-card__upload">
          {preview ? (
            <img className="agent-card__thumb" src={preview} alt="올린 대표 이미지" />
          ) : (
            <p className="agent-card__upload-hint">마켓에 보일 대표 이미지를 올려 주세요.</p>
          )}
          <input
            ref={fileInput}
            type="file"
            accept="image/*"
            hidden
            onChange={(event) => void handlePick(event.target.files?.[0])}
          />
          <button
            type="button"
            className="agent-card__upload-button"
            disabled={uploading || complete.isPending}
            onClick={() => fileInput.current?.click()}
          >
            {uploading ? '올리는 중…' : preview ? '다른 이미지로 바꾸기' : '이미지 올리기'}
          </button>
        </div>
      )}

      <StatusOrActions
        action={action}
        disabled={disabled}
        busyLabel="처리 중…"
        confirmLabel="확인"
        onConfirm={handleConfirm}
        isBusy={complete.isPending}
        onReject={() => void reject.mutateAsync(action.id)}
      />

      {error && <p className="agent-card__error">{error}</p>}
    </div>
  )
}

/**
 * 제품 등록 카드가 보여줄 값.
 *
 * <b>가격은 승인 전에 보여야 한다.</b> 카드에는 모델이 만든 한 줄 요약만 떴고, 가격·분류·설명은
 * 인자에만 들어 있었다. 담당자가 "전부 알아서 정해줘" 라고 한 뒤 25,000원을 모르고 승인했고,
 * 나중에 조회에서야 얼마인지 알았다 — 마켓에 붙는 값이고 되돌리려면 새 모델을 등록해야 한다.
 *
 * 인자를 읽지 못하면 아무것도 그리지 않는다. 모델이 만든 JSON 이라 형태가 어긋날 수 있고,
 * 그때는 요약만 남는 편이 깨진 칸을 보여주는 것보다 낫다.
 */
function ProductDetails({ payloadJson }: { payloadJson: string }) {
  const detail = parseProduct(payloadJson)
  if (!detail) return null

  return (
    <dl className="agent-card__fields">
      {detail.name && (
        <>
          <dt>제품명</dt>
          <dd>{detail.name}</dd>
        </>
      )}
      {typeof detail.price === 'number' && (
        <>
          <dt>판매가</dt>
          <dd>{detail.price.toLocaleString()}원</dd>
        </>
      )}
      {detail.category && (
        <>
          <dt>분류</dt>
          {/* 코드("WALLET")를 그대로 보이지 않는다. 수동 등록 화면과 같은 한글 이름이다. 등록 뒤에는
              바꿀 수 없어, 수동 화면처럼 그 사실을 함께 적는다. */}
          <dd>
            {categoryText(detail.category, detail.subcategory)}
            <small className="agent-card__note"> · 등록 뒤 바꿀 수 없습니다</small>
          </dd>
        </>
      )}
      {detail.description && (
        <>
          <dt>설명</dt>
          <dd>{detail.description}</dd>
        </>
      )}
    </dl>
  )
}

/** 세부 분류는 의류와 가방에만 있다. 쇼핑몰은 코드로 보여 주므로 한글 이름은 여기서만 쓴다. */
const SUBCATEGORY_LABEL: Record<string, string> = {
  TOP: '상의',
  OUTER: '아우터',
  PANTS: '바지',
  SKIRT: '치마',
  DRESS: '원피스',
  TOTE: '토트',
  SHOULDER: '숄더',
  CROSSBODY: '크로스바디',
  BACKPACK: '백팩',
}

function categoryText(category: string, subcategory?: string): string {
  const main = PRODUCT_CATEGORY_LABEL[category as ProductCategory] ?? category
  if (!subcategory) return main
  return `${main} / ${SUBCATEGORY_LABEL[subcategory] ?? subcategory}`
}

type ProductPayload = {
  name?: string
  price?: number
  category?: string
  subcategory?: string
  description?: string
}

/**
 * 투입 내역을 <b>숫자로</b> 보여 준다.
 *
 * 요약 한 줄("데님 6kg, 폴리에스터 2kg … 투입")로는 그중 얼마가 제품이 되고 얼마가 버려지는지
 * 알 수 없다. 그 배분이 제품에 붙는 업사이클 비율을 정하므로, 담당자가 승인 전에 눈으로
 * 확인할 수 있어야 한다.
 *
 * 숫자는 <b>서버가 풀어 둔 것</b>을 쓴다(AddProductionInputsTool.payloadExtras). 담당자는 투입량을 말하기도
 * 하고 제품에 들어간 양을 말하기도 해서, 모델이 넘긴 인자에는 둘 중 하나만 있다. 서버가 네 숫자를 모두
 * 채워 두며, 그것이 없을 때만 여기서 나머지를 계산한다.
 *
 * 수동 투입 화면과 같은 말과 같은 안내를 쓴다 — 자투리는 새 소재로 돌아오고, 폐기는 사라진다.
 *
 * 소재 한 줄을 이름과 숫자 두 줄로 쌓는다. 예전에는 "이름 | 숫자" 두 칸 격자에 줄마다 한 겹을 더 씌워,
 * 두 번째 소재가 좁은 오른쪽 칸으로 밀려 글자가 세로로 쌓였다.
 */
function ProductionInputDetails({ payloadJson }: { payloadJson: string }) {
  const inputs = parseInputs(payloadJson)
  if (inputs.length === 0) return null

  const noProduct = inputs.every((input) => input.productQuantity === 0)
  return (
    <div className="agent-card__inputs">
      <ul>
        {inputs.map((input) => (
          <li key={input.materialId}>
            <strong>{input.materialName ?? `소재 ${input.materialId}`}</strong>
            {/* 한 화면에서 단위가 섞이면 비교가 안 된다. 다른 곳과 같이 kg 으로 보인다. */}
            <span>
              투입 {formatKg(input.inputQuantity)} = 제품 사용 {formatKg(input.productQuantity)}
              {' + '}자투리 {formatKg(input.reusableScrapQuantity)}{' + '}폐기 {formatKg(input.wasteQuantity)}
            </span>
            {input.wasteReason && <small>폐기 사유: {input.wasteReason}</small>}
          </li>
        ))}
      </ul>
      <p className="agent-card__note">
        자투리는 생산을 확정할 때 새 소재로 등록되어 보유 소재로 돌아옵니다. 폐기는 그대로 사라집니다.
      </p>
      {noProduct && (
        <p className="agent-card__warning" role="alert">
          제품에 쓰인 수량이 없습니다. 투입이 전부 자투리·폐기면 제품을 만들 수 없어 생산을 확정할 수 없습니다.
        </p>
      )}
    </div>
  )
}

interface ProductionInputLine {
  materialId: number
  materialName?: string
  inputQuantity: number
  productQuantity: number
  reusableScrapQuantity: number
  wasteQuantity: number
  wasteReason?: string
}

interface ProductionInputPayload {
  materialId?: number
  materialName?: string
  inputQuantity?: number
  productQuantity?: number
  reusableScrapQuantity?: number
  wasteQuantity?: number
  wasteReason?: string
}

function parseInputs(payloadJson: string): ProductionInputLine[] {
  try {
    const parsed = JSON.parse(payloadJson) as { inputs?: ProductionInputPayload[] }
    return (parsed.inputs ?? []).flatMap((input) => {
      if (typeof input?.materialId !== 'number') return []
      const scrap = input.reusableScrapQuantity ?? 0
      const waste = input.wasteQuantity ?? 0
      // 서버가 풀어 둔 값이 없을 때만 계산한다 — 투입량이나 제품 사용 중 하나는 있다.
      const total = input.inputQuantity ?? (input.productQuantity !== undefined
        ? input.productQuantity + scrap + waste
        : undefined)
      if (total === undefined) return []
      const product = input.productQuantity ?? total - scrap - waste
      return [{
        materialId: input.materialId,
        materialName: input.materialName,
        inputQuantity: total,
        productQuantity: product,
        reusableScrapQuantity: scrap,
        wasteQuantity: waste,
        wasteReason: input.wasteReason,
      }]
    })
  } catch {
    // 모델이 만든 인자라 깨질 수 있다. 못 읽으면 요약만 보여 준다 — 카드를 못 쓰게 만들지 않는다.
    return []
  }
}

function parseProduct(payloadJson: string): ProductPayload | null {
  try {
    return JSON.parse(payloadJson) as ProductPayload
  } catch {
    return null
  }
}
