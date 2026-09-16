import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input, Loading, Select } from '../../../components/common/index.ts'
import { useMyProductModels } from '../../product/hooks/useMyProductModels.ts'
import { useMyWallets } from '../../wallet/hooks/useMyWallets.ts'
import { useOpenProductionBatch } from '../hooks/useOpenProductionBatch.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

export interface ProductionBatchFormProps {
  onOpened?: (batch: ProductionBatch) => void
}

interface BatchFormValues {
  batchName: string
  productModelId: number | ''
  producedQuantity: number
}

/**
 * 생산 배치 열기.
 *
 * 배치는 **"이 제품을 몇 개 만든다"** 는 선언이다. 여기서 소재를 고르지 않는다 — 투입은
 * 배치가 열린 뒤 별도 요청이고, 그래야 여러 소재를 모아 한 번에 잠글 수 있다.
 *
 * **제품은 내 제품에서만 고른다.** 서버도 남의 제품에는 배치를 열지 못하게 막지만
 * (`PRODUCTION_PRODUCT_MODEL_FORBIDDEN`), 고를 수 없게 하는 편이 낫다.
 *
 * ⚠️ **활성 지갑이 없으면 열 수 없다** (`PRODUCTION_WALLET_NOT_LINKED`). 서명자 주소를
 * 서버가 등록된 지갑에서 가져와 배치에 굳히기 때문이다. 제출해 보고 알게 하지 않고 미리
 * 막는다 — 지갑 등록은 다른 화면에서 해야 하는 일이라 되돌아오는 길이 길다.
 */
export function ProductionBatchForm({ onOpened }: ProductionBatchFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<BatchFormValues>({ defaultValues: { batchName: '', productModelId: '' } })

  const products = useMyProductModels()
  const wallets = useMyWallets()
  const open = useOpenProductionBatch()

  if (products.isPending || wallets.isPending) {
    return <Loading label="제품과 지갑 정보를 불러오는 중" />
  }
  if (products.isError) {
    return <ErrorMessage error={products.error} onRetry={() => void products.refetch()} />
  }

  // 지갑 조회가 실패하면 막지 않는다 — 서버가 최종 판단을 한다. 여기서 차단하면
  // 조회 실패만으로 생산 자체를 못 하게 된다.
  const hasWallet = wallets.isError || wallets.activeWallet !== null

  // 단종된 제품으로는 생산할 이유가 없다. 서버가 막지는 않지만 만들어도 팔 수 없다.
  const sellable = products.data.filter((product) => product.status !== 'DISCONTINUED')

  if (sellable.length === 0) {
    return (
      <p className="batch-form__note">
        생산할 제품이 없습니다. 제품을 먼저 등록해 주세요.
      </p>
    )
  }

  const onSubmit = handleSubmit((values) => {
    if (values.productModelId === '' || !hasWallet) return
    open.mutate(
      {
        batchName: values.batchName.trim(),
        productModelId: values.productModelId,
        producedQuantity: values.producedQuantity,
      },
      {
        onSuccess: (batch) => {
          reset()
          onOpened?.(batch)
        },
      },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate className="batch-form">
      <Input
        label="배치 이름"
        placeholder="예: 2026 가을 토트백 1차"
        error={errors.batchName?.message}
        {...register('batchName', {
          required: '배치 이름을 입력해 주세요.',
          maxLength: { value: 200, message: '배치 이름은 200자를 넘을 수 없습니다.' },
        })}
      />

      <Select
        label="생산할 제품"
        placeholder="제품을 선택하세요"
        options={sellable.map((product) => ({
          value: String(product.productModelId),
          label: product.name,
        }))}
        error={errors.productModelId?.message}
        {...register('productModelId', {
          valueAsNumber: true,
          // `required` 로는 못 잡는다 — valueAsNumber 가 미선택('')을 NaN 으로 바꾸고
          // required 는 NaN 을 값이 있는 것으로 본다. 그대로 두면 NaN 이 서버로 나간다.
          validate: (value) =>
            Number.isInteger(value) ? true : '생산할 제품을 선택해 주세요.',
        })}
      />

      <Input
        label="생산 개수"
        type="number"
        min={1}
        hint="확정 시 이 개수만큼 제품 개체가 만들어집니다."
        error={errors.producedQuantity?.message}
        {...register('producedQuantity', {
          required: '생산 개수를 입력해 주세요.',
          valueAsNumber: true,
          min: { value: 1, message: '1개 이상 입력해 주세요.' },
          validate: (value) =>
            Number.isInteger(value) ? true : '생산 개수는 정수로 입력해 주세요.',
        })}
      />

      {/* 지갑이 없으면 서버가 거절한다. 등록은 다른 화면이라 미리 알려주고 데려간다. */}
      {!hasWallet && (
        <p className="batch-form__note batch-form__note--warning">
          생산에는 등록된 지갑이 필요합니다. 회사 정보에서 지갑을 먼저 연결해 주세요.
        </p>
      )}

      {open.isError && <ErrorMessage error={open.error} />}

      <Button type="submit" loading={open.isPending} disabled={!hasWallet}>
        배치 열기
      </Button>
    </form>
  )
}
