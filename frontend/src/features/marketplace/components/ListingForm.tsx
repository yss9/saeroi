import { useForm, useWatch } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { calculateTotalPrice, formatKg, formatWon, kgToGram } from '../../../utils/quantity.ts'
import { useCreateListing } from '../hooks/useCreateListing.ts'

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

  const { mutate, isPending, isError, error } = useCreateListing()

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

  const onSubmit = handleSubmit((values) => {
    mutate(
      {
        materialId,
        quantity: kgToGram(values.quantityKg),
        pricePerKg: values.pricePerKg,
        description: values.description,
      },
      {
        onSuccess: (created) => {
          reset()
          onCreated?.(created.id)
        },
      },
    )
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

      <Input label="설명 (선택)" {...register('description')} />

      {previewTotal !== null && (
        <p className="listing-form__preview">
          예상 판매 금액 <strong>{formatWon(previewTotal)}</strong>
        </p>
      )}

      {isError && <ErrorMessage error={error} />}

      <Button type="submit" loading={isPending} fullWidth>
        판매 등록
      </Button>
    </form>
  )
}
