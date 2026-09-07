import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input, Select } from '../../../components/common/index.ts'
import { kgToGram } from '../../../utils/quantity.ts'
import { useCreateMaterial } from '../hooks/useCreateMaterial.ts'
import { WIRE_UNIT } from '../types/materialQuantity.ts'
import {
  GRADE_LABEL,
  ROOT_ORIGIN_LABEL,
  type CreateMaterialRequest,
  type MaterialGrade,
  type MaterialRootOrigin,
  type MaterialType,
} from '../types/material.types.ts'

export interface MaterialFormProps {
  onCreated?: (materialId: number) => void
}

/**
 * 화면이 다루는 폼 값. **전송 본문과 다르다.**
 *
 * 사용자는 kg 로 입력하지만 서버는 gram 정수만 받으므로, 제출 시점에
 * `kgToGram()` 으로 환산해 `CreateMaterialRequest` 로 바꾼다.
 * 두 모양을 한 타입으로 쓰면 "지금 이 숫자가 kg 인가 gram 인가" 를 매번 추론해야 한다.
 */
interface MaterialFormValues {
  materialType: MaterialType | ''
  rootOrigin: MaterialRootOrigin
  name: string
  /** 사용자 입력 단위는 kg 다. 소수 허용 */
  quantityKg: number
  grade: MaterialGrade | ''
  description: string
  lengthMm: number | null
  widthMm: number | null
}

const MATERIAL_TYPE_OPTIONS: readonly { value: MaterialType; label: string }[] = [
  { value: 'DENIM', label: '데님' },
  { value: 'COTTON', label: '면' },
  { value: 'LEATHER', label: '가죽' },
  { value: 'LINING', label: '안감' },
  { value: 'METAL', label: '금속' },
  { value: 'MIXED', label: '혼방' },
  { value: 'ETC', label: '기타' },
]

// RECOVERED 는 없다. 정책 v3에서 회수 소재 재순환이 MVP 범위에서 제외됐고
// Backend ENUM 도 VIRGIN/WASTE 두 값뿐이다 — 보내면 400 이 된다.
const ROOT_ORIGIN_OPTIONS: readonly { value: MaterialRootOrigin; label: string }[] = [
  { value: 'WASTE', label: ROOT_ORIGIN_LABEL.WASTE },
  { value: 'VIRGIN', label: ROOT_ORIGIN_LABEL.VIRGIN },
]

const GRADE_OPTIONS: readonly { value: MaterialGrade; label: string }[] = [
  { value: 'A', label: GRADE_LABEL.A },
  { value: 'B', label: GRADE_LABEL.B },
  { value: 'C', label: GRADE_LABEL.C },
]

/** 서버 `total_quantity` 는 BIGINT 지만, 실수로 자릿수를 더 누른 입력을 여기서 먼저 막는다. */
const MAX_QUANTITY_KG = 1_000_000

/**
 * 소재 등록 폼.
 *
 * <b>입력값마다 useState 를 두지 않는다.</b> 필드가 8개이고 Processing·Production 등록은
 * 더 많다. 개별 useState 로는 검증·에러 표시·초기화가 흩어지고 입력할 때마다 폼 전체가
 * 리렌더된다.
 *
 * ── 서버 계약에서 일부러 빼놓은 것 ──
 * `materialCode`(서버가 id 에서 파생) · `generationType`(항상 INITIAL) ·
 * `registrantId`(JWT sub) 는 입력칸이 없다. 자세한 이유는 `CreateMaterialRequest` 주석 참고.
 *
 * ── 아직 못 넣은 입력 2개 ──
 * 티켓(S15P21E104-152)은 "구성" 과 "발생 사유" 도 요구하지만 Backend 에 저장할 컬럼이
 * 없고 Jira·API 명세 어디에도 없다. 관리자에게 확인 요청 중이며, 컬럼이 생기면 여기에
 * 추가한다. **입력칸을 미리 만들어 두지 않는다** — 값이 서버에서 버려지는데 화면에
 * 보이면 사용자가 입력한 것이 사라진다.
 *
 * "이미지" 도 마찬가지로 빠져 있다. 증빙 업로드 경로(Evidence)가 아직 없다.
 */
export function MaterialForm({ onCreated }: MaterialFormProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<MaterialFormValues>({
    defaultValues: {
      materialType: '',
      rootOrigin: 'WASTE',
      name: '',
      grade: '',
      description: '',
      lengthMm: null,
      widthMm: null,
    },
  })

  const { mutate, isPending, isError, error } = useCreateMaterial()

  const onSubmit = handleSubmit((values) => {
    mutate(toRequest(values), {
      onSuccess: (created) => {
        reset()
        onCreated?.(created.materialId)
      },
    })
  })

  return (
    <form onSubmit={onSubmit} noValidate>
      <Select
        label="소재 유형"
        placeholder="선택해 주세요"
        options={MATERIAL_TYPE_OPTIONS}
        error={errors.materialType?.message}
        {...register('materialType', { required: '소재 유형을 선택해 주세요.' })}
      />

      <Input
        label="소재명"
        placeholder="폐데님 원단"
        error={errors.name?.message}
        {...register('name', {
          required: '소재명을 입력해 주세요.',
          maxLength: { value: 200, message: '소재명은 200자를 넘을 수 없습니다.' },
        })}
      />

      <Select
        label="출처"
        options={ROOT_ORIGIN_OPTIONS}
        error={errors.rootOrigin?.message}
        {...register('rootOrigin', { required: '출처를 선택해 주세요.' })}
      />

      <Input
        label="수량 (kg)"
        type="number"
        step="0.001"
        placeholder="20.5"
        error={errors.quantityKg?.message}
        {...register('quantityKg', {
          // input[type=number] 도 값은 문자열로 온다. 숫자로 변환해 두면 아래 검증이
          // 문자열 비교로 새지 않는다.
          valueAsNumber: true,
          required: '수량을 입력해 주세요.',
          validate: (value) => {
            if (!Number.isFinite(value)) return '수량을 숫자로 입력해 주세요.'
            if (value <= 0) return '수량은 0보다 커야 합니다.'
            if (value > MAX_QUANTITY_KG) return `수량은 ${MAX_QUANTITY_KG.toLocaleString('ko-KR')}kg 이하여야 합니다.`
            // 1g 미만은 저장 단위(gram 정수)로 표현할 수 없다. 0.0004kg 을 받아
            // 0g 으로 저장하면 서버가 "수량은 양수" 로 거절해 원인이 드러나지 않는다.
            if (kgToGram(value) < 1) return '수량은 최소 1g(0.001kg) 이상이어야 합니다.'
            return true
          },
        })}
      />

      <Select
        label="등급 (선택)"
        placeholder="선택 안 함"
        options={GRADE_OPTIONS}
        error={errors.grade?.message}
        {...register('grade')}
      />

      <Input
        label="설명 (선택)"
        placeholder="봉제 공장에서 회수한 자투리 원단"
        error={errors.description?.message}
        {...register('description')}
      />

      <Input
        label="길이 (mm, 선택)"
        type="number"
        error={errors.lengthMm?.message}
        {...register('lengthMm', {
          setValueAs: toOptionalNumber,
          validate: (value) =>
            value === null || value > 0 || '길이는 0보다 커야 합니다.',
        })}
      />

      <Input
        label="너비 (mm, 선택)"
        type="number"
        error={errors.widthMm?.message}
        {...register('widthMm', {
          setValueAs: toOptionalNumber,
          validate: (value) =>
            value === null || value > 0 || '너비는 0보다 커야 합니다.',
        })}
      />

      {isError && <ErrorMessage error={error} />}

      <Button type="submit" loading={isPending}>
        소재 등록
      </Button>
    </form>
  )
}

/**
 * 폼 값 → 전송 본문.
 *
 * <b>수량 환산이 이 함수의 존재 이유다.</b> 서버 `quantity` 는 정수만 받으므로
 * `20.5 kg` 을 그대로 보내면 400 이 된다. `kgToGram()` 으로 gram 정수로 바꿔
 * `unit: 'G'` 로 보낸다. 그 함수를 쓰는 것이 중요하다 — `20.3 * 1000` 은
 * 부동소수점 때문에 `20299.999…` 가 되고, 직접 곱하면 1g 이 사라진다.
 *
 * 빈 문자열은 서버에 보내지 않는다. `grade` 에 `''` 를 보내면 자유 텍스트 컬럼이라
 * DB 에 빈 문자열이 그대로 저장되고, 그건 "등급 없음"(null)과 다른 값이 된다.
 */
function toRequest(values: MaterialFormValues): CreateMaterialRequest {
  return {
    materialType: values.materialType as MaterialType,
    rootOrigin: values.rootOrigin,
    name: values.name.trim(),
    quantity: kgToGram(values.quantityKg),
    unit: WIRE_UNIT,
    ...(values.grade ? { grade: values.grade } : {}),
    ...(values.description.trim() ? { description: values.description.trim() } : {}),
    ...(values.lengthMm !== null ? { lengthMm: values.lengthMm } : {}),
    ...(values.widthMm !== null ? { widthMm: values.widthMm } : {}),
  }
}

/** 빈 입력을 0 으로 만들지 않는다. "미입력"(null)과 "0 을 입력함" 은 다르게 검증해야 한다. */
function toOptionalNumber(raw: unknown): number | null {
  if (raw === '' || raw === null || raw === undefined) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}
