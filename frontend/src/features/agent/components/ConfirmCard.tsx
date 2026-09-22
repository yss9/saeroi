import { useRef, useState } from 'react'
import { useCompleteAction, useRejectAction } from '../hooks/useAgentConversation.ts'
import { StatusOrActions } from './PaymentCard.tsx'
import { uploadProductImage } from '../../product/api/uploadProductImage.ts'
import type { PendingAction } from '../types/agent.types.ts'

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
          <dd>{detail.subcategory ? `${detail.category} / ${detail.subcategory}` : detail.category}</dd>
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

type ProductPayload = {
  name?: string
  price?: number
  category?: string
  subcategory?: string
  description?: string
}

function parseProduct(payloadJson: string): ProductPayload | null {
  try {
    return JSON.parse(payloadJson) as ProductPayload
  } catch {
    return null
  }
}
