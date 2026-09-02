import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input, Select } from '../../../components/common/index.ts'
import { useCreateMaterial } from '../hooks/useCreateMaterial.ts'
import type { CreateMaterialRequest } from '../types/material.types.ts'

export interface MaterialFormProps {
  onCreated?: (materialId: number) => void
}

const ROOT_ORIGIN_OPTIONS = [
  { value: 'WASTE', label: '폐기물' },
  { value: 'RECOVERED', label: '회수 소재' },
  { value: 'VIRGIN', label: '신소재' },
] as const

/**
 * 소재 등록 폼 — React Hook Form 사용 예시.
 *
 * 입력값마다 useState 를 두지 않는다. 이 폼만 해도 필드가 6개이고, Processing 결과
 * 입력이나 Production 등록은 더 많다. 개별 useState 로는 검증·에러 표시·초기화가
 * 흩어지고 입력할 때마다 폼 전체가 리렌더된다.
 */
export function MaterialForm({ onCreated }: MaterialFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateMaterialRequest>({
    defaultValues: { rootOrigin: 'WASTE', generationType: 'INITIAL' },
  })

  const { mutate, isPending, isError, error } = useCreateMaterial()

  const onSubmit = handleSubmit((values) => {
    mutate(values, {
      onSuccess: (created) => {
        reset()
        onCreated?.(created.id)
      },
    })
  })

  return (
    <form onSubmit={onSubmit} noValidate>
      <Input
        label="소재 코드"
        placeholder="MAT-2026-0001"
        error={errors.materialCode?.message}
        {...register('materialCode', { required: '소재 코드를 입력해 주세요.' })}
      />

      <Input
        label="소재명"
        error={errors.name?.message}
        {...register('name', { required: '소재명을 입력해 주세요.' })}
      />

      <Input
        label="소재 종류"
        placeholder="DENIM"
        error={errors.materialType?.message}
        {...register('materialType', { required: '소재 종류를 입력해 주세요.' })}
      />

      <Select
        label="출처"
        options={ROOT_ORIGIN_OPTIONS}
        error={errors.rootOrigin?.message}
        {...register('rootOrigin', { required: true })}
      />

      <Input
        label="총 수량"
        type="number"
        error={errors.totalQuantity?.message}
        {...register('totalQuantity', {
          required: '수량을 입력해 주세요.',
          // input[type=number] 도 값은 문자열로 온다. Backend 가 숫자를 기대하므로 변환한다.
          valueAsNumber: true,
          min: { value: 1, message: '수량은 1 이상이어야 합니다.' },
        })}
      />

      {isError && <ErrorMessage error={error} />}

      <Button type="submit" loading={isPending}>
        소재 등록
      </Button>
    </form>
  )
}
