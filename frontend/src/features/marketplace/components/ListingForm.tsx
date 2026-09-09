import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { calculateTotalPrice, formatKg, formatWon, kgToGram } from '../../../utils/quantity.ts'
import { useMaterial } from '../../material/hooks/useMaterial.ts'
import { useCreateListing } from '../hooks/useCreateListing.ts'
import { useListingOnChain } from '../hooks/useListingOnChain.ts'
import type { CreateListingRequest, Listing } from '../types/marketplace.types.ts'

export interface ListingFormProps {
  /** 판매할 소재 */
  materialId: number
  materialName: string
  /** 이 소재의 가용 수량 (gram). Lock 을 뺀 실제로 팔 수 있는 양 */
  availableQuantity: number
  onCreated?: (listingId: number) => void
}

/** 폼 입력값. 사용자는 kg 으로 입력하고 전송 직전에 gram 으로 바꾼다. */
interface ListingFormValues {
  quantityKg: number
  pricePerKg: number
  description?: string
}

/**
 * 판매 등록 폼 (Supplier Market).
 *
 * 두 가지가 이 폼의 핵심이다.
 *
 *   1) **가용 수량을 넘을 수 없다.** 넘겨 등록하면 서버가 Lock 을 만들지 못해 실패한다.
 *      서버도 검사하지만, 여기서 막아야 사용자가 제출 후에야 알게 되는 일이 없다.
 *   2) **kg → gram 변환은 제출 시점에 한 번만** 한다. 화면은 kg 으로 다루고
 *      저장·전송은 gram 정수다 (정책 03절).
 */
export function ListingForm({
  materialId,
  materialName,
  availableQuantity,
  onCreated,
}: ListingFormProps) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ListingFormValues>()

  const { mutateAsync, isPending, isError, error } = useCreateListing()

  // 입력 중인 값으로 총액을 미리 보여준다. 확정 금액이 아니라 미리보기다 —
  // 실제 금액은 서버가 Listing 의 단가로 계산한다.
  //
  // watch() 대신 useWatch() 를 쓴다. watch() 가 돌려주는 함수는 렌더마다 새로 만들어져
  // 안전하게 메모이제이션할 수 없다(react-hooks/incompatible-library).
  const quantityKg = useWatch({ control, name: 'quantityKg' })
  const pricePerKg = useWatch({ control, name: 'pricePerKg' })
  const previewTotal =
    quantityKg > 0 && pricePerKg > 0 ? calculateTotalPrice(kgToGram(quantityKg), pricePerKg) : null

  const availableKg = availableQuantity / 1000

  // 온체인 등록에 필요한 tokenId. 소재가 아직 발행되지 않았으면 null 이고, 그때는
  // 서버 등록만 하고 끝낸다 — 판매 자체를 막을 이유는 없다.
  const material = useMaterial(materialId)
  const tokenId = material.data?.tokenId ?? null
  const chain = useListingOnChain()
  const [pendingListing, setPendingListing] = useState<{
    created: Listing
    request: CreateListingRequest
  } | null>(null)

  const onSubmit = handleSubmit(async (values) => {
    try {
      const request: CreateListingRequest = pendingListing?.request ?? {
        materialId,
        // kg 을 gram 으로 바꿔 'G' 로 보낸다. kgToGram 이 Math.round 를 쓰는 것이
        // 중요하다 — 20.3 * 1000 은 20299.999... 라 그냥 곱하면 1g 이 사라진다.
        quantity: kgToGram(values.quantityKg),
        unit: 'G',
        pricePerKg: values.pricePerKg,
      }
      const created = pendingListing?.created ?? (await mutateAsync(request))
      if (pendingListing === null) setPendingListing({ created, request })

      if (tokenId === null) {
        onCreated?.(created.listingId)
        return
      }

      const linked = await chain.register(created.listingId, tokenId, request.quantity)
      if (linked) {
        setPendingListing(null)
        reset()
        onCreated?.(created.listingId)
      }
    } catch {
      // API와 지갑 오류는 각 훅의 오류 상태로 화면에 표시한다.
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="listing-form">
      <p className="listing-form__material">
        <strong>{materialName}</strong>
        <span> · 판매 가능 {formatKg(availableQuantity)}</span>
      </p>

      <Input
        label="판매 수량 (kg)"
        type="number"
        step="0.001"
        hint={`최대 ${formatKg(availableQuantity, { withUnit: false })} kg`}
        error={errors.quantityKg?.message}
        {...register('quantityKg', {
          required: '판매 수량을 입력해 주세요.',
          valueAsNumber: true,
          min: { value: 0.001, message: '0보다 큰 수량을 입력해 주세요.' },
          // 서버도 검사하지만 여기서 먼저 막는다. 제출 후에야 알게 되면 사용자가 헛수고한다.
          max: {
            value: availableKg,
            message: `판매 가능 수량(${availableKg}kg)을 넘을 수 없습니다.`,
          },
        })}
      />

      <Input
        label="kg당 가격 (원)"
        type="number"
        error={errors.pricePerKg?.message}
        {...register('pricePerKg', {
          required: '가격을 입력해 주세요.',
          valueAsNumber: true,
          min: { value: 1, message: '1원 이상 입력해 주세요.' },
        })}
      />

      {/* 설명 입력이 없다. 판매글에는 설명 컬럼이 없고 소재의 설명을 그대로 보여준다 —
          같은 소재를 두 번 올리면 설명이 갈라져 어느 쪽이 그 소재의 사실인지 알 수 없게 된다. */}

      {previewTotal !== null && (
        <p className="listing-form__preview">
          예상 판매 금액 <strong>{formatWon(previewTotal)}</strong>
        </p>
      )}

      {isError && <ErrorMessage error={error} />}
      {chain.error && !chain.isRejected && <ErrorMessage error={chain.error} />}
      {chain.isRejected && (
        <p className="listing-form__preview">
          판매 등록은 끝났습니다. 온체인 서명을 취소하셨으니 판매글 상세에서 다시 할 수
          있습니다.
        </p>
      )}

      {/* 어느 단계인지 말해 준다 — 지갑을 봐야 하는지 기다리면 되는지가 다르다. */}
      <Button type="submit" loading={isPending || chain.isBusy} fullWidth>
        {chain.status === 'SIGNING'
          ? '지갑에서 서명해 주세요'
          : chain.status === 'PENDING'
            ? '블록 확정을 기다리는 중…'
            : chain.status === 'LINKING'
              ? '온체인 결과를 기록하는 중…'
              : pendingListing
                ? '판매 등록 완료하기'
                : '판매 등록'}
      </Button>

      {tokenId === null && (
        <p className="listing-form__preview">
          이 소재는 아직 온체인에 발행되지 않아 서버에만 등록됩니다. 소재 상세에서 발행한 뒤
          판매글 상세에서 온체인 등록을 할 수 있습니다.
        </p>
      )}
    </form>
  )
}
