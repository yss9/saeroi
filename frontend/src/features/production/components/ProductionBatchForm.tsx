import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input, Loading } from '../../../components/common/index.ts'
import { useMyWallets } from '../../wallet/hooks/useMyWallets.ts'
import { useOpenProductionBatch } from '../hooks/useOpenProductionBatch.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

export interface ProductionBatchFormProps {
  onOpened?: (batch: ProductionBatch) => void
}

interface BatchFormValues {
  batchName: string
  producedQuantity: number
}

/**
 * 생산 배치 열기.
 *
 * 배치는 **"이번에 얼마나 만든다"** 는 선언이다. 여기서 소재도, 완성 제품도 고르지 않는다 —
 * 둘 다 배치가 열린 뒤에 정한다. 투입은 여러 소재를 모아 한 번에 잠가야 하고, 무엇을
 * 만들지는 소재를 다 넣고 나서 정해도 늦지 않다 (확정 전까지만 채우면 된다).
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
  } = useForm<BatchFormValues>({ defaultValues: { batchName: '' } })

  const wallets = useMyWallets()
  const open = useOpenProductionBatch()

  if (wallets.isPending) {
    return <Loading label="지갑 정보를 불러오는 중" />
  }

  // 지갑 조회가 실패하면 막지 않는다 — 서버가 최종 판단을 한다. 여기서 차단하면
  // 조회 실패만으로 생산 자체를 못 하게 된다.
  const hasWallet = wallets.isError || wallets.activeWallet !== null

  const onSubmit = handleSubmit((values) => {
    if (!hasWallet) return
    open.mutate(
      {
        batchName: values.batchName.trim(),
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

      <Input
        label="생산 개수"
        type="number"
        min={1}
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
