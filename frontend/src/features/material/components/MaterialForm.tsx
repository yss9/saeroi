import { useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Button, ErrorMessage, Input, Select } from '../../../components/common/index.ts'
import { kgToGram } from '../../../utils/quantity.ts'
import { useCreateMaterial } from '../hooks/useCreateMaterial.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useMintMaterialOnChain } from '../hooks/useMintMaterialOnChain.ts'
import type { ChainLinkStatus } from '../../../hooks/useOnChainLink.ts'
import { evidencePlaceholder } from '../services/evidencePlaceholder.ts'
import './material.css'
import { WIRE_UNIT } from '../types/materialQuantity.ts'
import {
  COMPOSITION_MAX_TOTAL,
  FIBER_TYPE_LABEL,
  GRADE_LABEL,
  MATERIAL_FIBER_TYPES,
  ORIGIN_REASON_LABEL,
  ROOT_ORIGIN_LABEL,
  originReasonRequiresNote,
  type CreateMaterialRequest,
  type CreateMaterialResponse,
  type MaterialFiberType,
  type MaterialGrade,
  type MaterialOriginReason,
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
  /** 출처가 WASTE 일 때만 쓴다. 빈 문자열은 "선택 안 함" */
  originReason: MaterialOriginReason | ''
  originReasonNote: string
  /**
   * 혼방 비율. `percentage` 가 `number | null` 인 이유는 빈 줄을 0 으로 만들지 않기
   * 위해서다 — 사용자가 섬유만 고르고 비율을 아직 안 적은 상태와 "0%" 는 다르다.
   */
  composition: { fiberType: MaterialFiberType | ''; percentage: number | null }[]
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

// PRODUCTION_SCRAP 은 없다. 그건 generationType 이 말하는 것이고, 같은 사실을 두 곳에
// 적으면 어긋난다. 서버 ENUM 에도 없어서 보내면 400 이 된다.
const ORIGIN_REASON_OPTIONS: readonly { value: MaterialOriginReason; label: string }[] = (
  ['DEADSTOCK', 'DEFECT', 'OFFCUT', 'END_OF_LIFE', 'COLLECTED', 'ETC'] as const
).map((value) => ({ value, label: ORIGIN_REASON_LABEL[value] }))

const FIBER_TYPE_OPTIONS: readonly { value: MaterialFiberType; label: string }[] =
  MATERIAL_FIBER_TYPES.map((value) => ({ value, label: FIBER_TYPE_LABEL[value] }))

/** 서버 `total_quantity` 는 BIGINT 지만, 실수로 자릿수를 더 누른 입력을 여기서 먼저 막는다. */
const MAX_QUANTITY_KG = 1_000_000

/**
 * 온체인 발행 단계별 진행률 표시.
 *
 * 실제 블록 승인 수·가스 소모 같은 값을 알 수 없어 <b>단계에 고정값을 붙인 근사치</b>다.
 * 그래도 없는 것보다 낫다 — 등록 버튼을 누른 뒤 화면이 몇 초간 가만히 있으면 사용자는
 * 멈췄다고 오해하고 새로고침하거나 다시 누른다. IDLE·FAILED 는 표에 없다 —
 * 진행 중이 아니거나 에러 메시지가 대신 보여준다.
 */
const MINT_STAGE: Partial<Record<ChainLinkStatus, { percent: number; label: string }>> = {
  SIGNING: { percent: 20, label: '지갑 서명을 기다리는 중' },
  PENDING: { percent: 55, label: '블록 확정을 기다리는 중' },
  CONFIRMED: { percent: 80, label: '온체인 결과를 기록하는 중' },
  LINKING: { percent: 80, label: '온체인 결과를 기록하는 중' },
  LINKED: { percent: 100, label: '발행 완료' },
}

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
 * ── 구성 · 발생 사유 (S15P21E104-437) ──
 * 저장 경로가 생겨서(S15P21E104-436) 입력칸을 붙였다. 그 전에는 값이 서버에서 버려지므로
 * 일부러 만들지 않았다 — 화면에 보이는데 사라지는 것이 없는 것보다 나쁘다.
 *
 * **발생 사유는 출처가 `WASTE` 일 때만 보인다.** 신소재에 사유를 보내면 서버가 400 을
 * 낸다("신소재에는 발생 사유가 붙을 수 없다"). 칸을 감추는 것으로 그 오류를 없앤다.
 *
 * **비율 합계 100 을 요구하지 않는다.** "면 60% 는 확실하고 나머지는 모른다" 가 흔하고,
 * 100 을 강제하면 사용자가 "기타 40%" 를 지어낸다. 그 값이 Upcycle Ratio 집계로 흘러간다.
 *
 * "이미지" 는 아직 빠져 있다. 증빙 업로드 경로(Evidence)가 없다.
 */
export function MaterialForm({ onCreated }: MaterialFormProps) {
  const {
    control,
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
      originReason: '',
      originReasonNote: '',
      composition: [],
    },
  })

  // 감시하는 이유가 다르다. rootOrigin·originReason 은 어떤 칸을 보일지 결정하고,
  // composition 은 합계를 실시간으로 보여주기 위한 것이다.
  const rootOrigin = useWatch({ control, name: 'rootOrigin' })
  const originReason = useWatch({ control, name: 'originReason' })
  const composition = useWatch({ control, name: 'composition' })

  const { fields, append, remove } = useFieldArray({ control, name: 'composition' })

  const isWaste = rootOrigin === 'WASTE'
  const compositionTotal = (composition ?? []).reduce(
    (sum, row) => sum + (row?.percentage ?? 0),
    0,
  )

  const { mutateAsync, isPending, isError, error } = useCreateMaterial()

  // 발행은 등록자 지갑이 서명한다 — 서버에는 개인키가 없다.
  const wallet = useWallet()
  const mint = useMintMaterialOnChain()
  const [pendingRegistration, setPendingRegistration] = useState<{
    created: CreateMaterialResponse
    request: CreateMaterialRequest
  } | null>(null)
  // 제출을 눌러 봤는데 지갑이 없었다. 처음부터 경고를 띄우지 않는 이유는, 지갑 복원이
  // 비동기라 화면이 뜨자마자 잠깐 `null` 이기 때문이다. 그때 경고부터 보이면 정상적으로
  // 연결된 사용자에게도 한 번 번쩍인다.
  const [walletRequired, setWalletRequired] = useState(false)

  const onSubmit = handleSubmit(async (values) => {
    // **지갑이 없으면 DB 에 손대기 전에 멈춘다.**
    //
    // 예전에는 소재를 먼저 만들고 발행만 조용히 건너뛰었다. 화면은 성공으로 넘어가는데
    // 체인에는 아무것도 없고, 사용자는 등록됐다고 믿는다. 온체인 증명이 이 서비스의
    // 존재 이유라 그 상태는 등록이 아니라 **영영 체인에 오르지 않을 고아**다.
    //
    // 특히 LAN 으로 붙는 팀원에게서 그대로 드러났다 — `192.168.30.118:5173` 은
    // `localhost:5173` 과 다른 origin 이라 MetaMask 연결 승인이 따로 필요한데,
    // 그 사실이 어디에도 표시되지 않아 "왜 서명 창이 안 뜨지" 로 시간을 썼다.
    //
    // 순서가 중요하다. `mutateAsync` 뒤에서 막으면 이미 만들어진 소재가 남는다.
    if (wallet.address === null) {
      setWalletRequired(true)
      return
    }
    setWalletRequired(false)

    try {
      // 서명 또는 서버 연결이 실패하면 이미 만든 DB 소재를 기억한다. 다음 제출에서는
      // 소재를 또 만들지 않고 같은 소재의 온체인 단계만 이어서 수행한다.
      const request = pendingRegistration?.request ?? toRequest(values)
      const created = pendingRegistration?.created ?? (await mutateAsync(request))
      if (pendingRegistration === null) setPendingRegistration({ created, request })

      const linked = await mint.mint({
        materialId: created.materialId,
        to: wallet.address,
        amount: request.quantity,
        rootOrigin: request.rootOrigin,
        metadataUri: `saeroi://material/${created.materialCode}`,
        evidenceHash: evidencePlaceholder(created.materialCode),
      })

      if (linked) {
        setPendingRegistration(null)
        reset()
        onCreated?.(created.materialId)
      }
    } catch {
      // API와 지갑 오류는 각 훅의 오류 상태로 화면에 표시한다.
    }
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
        step="0.1"
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

      {/*
        발생 사유는 출처가 WASTE 일 때만 보인다. 신소재에 사유를 보내면 서버가 400 을
        내므로, 칸을 감추는 것이 오류 메시지를 다듬는 것보다 낫다.
      */}
      {isWaste && (
        <Select
          label="발생 사유 (선택)"
          placeholder="선택 안 함"
          options={ORIGIN_REASON_OPTIONS}
          error={errors.originReason?.message}
          {...register('originReason')}
        />
      )}

      {/* ETC 는 값 자체가 뜻을 담지 않아 설명이 없으면 분류가 무의미해진다. */}
      {isWaste && originReasonRequiresNote(originReason as MaterialOriginReason) && (
        <Input
          label="발생 사유 설명"
          placeholder="행사 후 남은 배너 원단"
          error={errors.originReasonNote?.message}
          {...register('originReasonNote', {
            required: '기타를 선택하면 설명이 필요합니다.',
            maxLength: { value: 200, message: '설명은 200자를 넘을 수 없습니다.' },
          })}
        />
      )}

      <fieldset>
        <legend>구성 (선택)</legend>
        {/*
          합계 100 을 요구하지 않는다. "면 60% 는 확실하고 나머지는 모른다" 가 흔하고,
          강제하면 사용자가 "기타 40%" 를 지어낸다. 대신 합계를 보여줘 스스로 판단하게 한다.
        */}
        <p aria-live="polite">
          합계 {compositionTotal.toLocaleString('ko-KR')}%
          {compositionTotal > COMPOSITION_MAX_TOTAL && ' — 100%를 넘을 수 없습니다.'}
          {compositionTotal > 0 && compositionTotal < COMPOSITION_MAX_TOTAL && ' (나머지 미확인)'}
        </p>

        {fields.map((field, index) => (
          <div key={field.id}>
            <Select
              label={`섬유 ${index + 1}`}
              placeholder="선택해 주세요"
              options={FIBER_TYPE_OPTIONS}
              error={errors.composition?.[index]?.fiberType?.message}
              {...register(`composition.${index}.fiberType` as const, {
                required: '섬유 종류를 선택해 주세요.',
              })}
            />
            <Input
              label={`비율 ${index + 1} (%)`}
              type="number"
              step="0.01"
              error={errors.composition?.[index]?.percentage?.message}
              {...register(`composition.${index}.percentage` as const, {
                setValueAs: toOptionalNumber,
                validate: (value) => {
                  if (value === null) return '비율을 입력해 주세요.'
                  if (value <= 0) return '비율은 0보다 커야 합니다.'
                  if (value > COMPOSITION_MAX_TOTAL) return '비율은 100%를 넘을 수 없습니다.'
                  return true
                },
              })}
            />
            <Button type="button" variant="secondary" size="sm" onClick={() => remove(index)}>
              섬유 {index + 1} 삭제
            </Button>
          </div>
        ))}

        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => append({ fiberType: '', percentage: null })}
        >
          섬유 추가
        </Button>
      </fieldset>

      {isError && <ErrorMessage error={error} />}
      {mint.error && !mint.isRejected && <ErrorMessage error={mint.error} />}

      {/*
        지갑 없이 제출했을 때만 보여준다. 무엇을 해야 하는지까지 적는다 —
        "지갑을 연결해 주세요" 만으로는, 다른 기기에서 이미 연결해 둔 사용자가
        왜 또 연결해야 하는지 알 수 없다. 승인은 주소(origin)마다 따로다.
      */}
      {walletRequired && wallet.address === null && (
        <div className="material-form__wallet-required" role="alert">
          <p>
            <strong>지갑을 먼저 연결해 주세요.</strong> 소재는 등록과 동시에 온체인에
            발행되고, 그 서명은 등록자 지갑만 할 수 있습니다.
          </p>
          <p>
            다른 주소(예: <code>localhost</code>)에서 연결해 두었더라도 <b>지금 주소에서는
            다시 승인</b>해야 합니다. MetaMask 의 연결 승인은 주소마다 따로 저장됩니다.
          </p>
          <Button type="button" variant="secondary" size="sm" onClick={wallet.connect}>
            지갑 연결
          </Button>
        </div>
      )}

      {/*
        온체인 발행 진행 표시. 등록 버튼을 누르면 서버 저장 → 지갑 서명 → 블록 확정 →
        서버 기록까지 한 번에 이어지는데, 그 사이 화면이 조용하면 사용자는 멈췄다고
        오해한다. 이 패널이 떠 있는 동안은 버튼 텍스트로 단계를 또 설명하지 않는다 —
        같은 정보를 두 곳에 쓰면 한쪽만 고쳤을 때 어긋난다.
      */}
      {MINT_STAGE[mint.status] && (
        <div className="material-form__mint-progress" role="status">
          <p>
            소재를 온체인에 발행하는 중입니다. 완료되면 자동으로 보유 소재 목록으로
            이동합니다.
          </p>
          <progress max={100} value={MINT_STAGE[mint.status]?.percent} />
          <span>
            {MINT_STAGE[mint.status]?.label} ({MINT_STAGE[mint.status]?.percent}%)
          </span>
        </div>
      )}

      <Button type="submit" loading={isPending || mint.isBusy}>
        {pendingRegistration && !mint.isBusy ? '소재 등록 완료하기' : '소재 등록'}
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
    ...toOriginReason(values),
    ...toComposition(values),
  }
}

/**
 * 발생 사유를 전송 본문으로.
 *
 * <b>신소재면 아무것도 보내지 않는다.</b> 칸이 감춰져 있어도 사용자가 WASTE 로 골라
 * 사유를 채운 뒤 VIRGIN 으로 되돌리면 폼 값에는 사유가 남아 있다. 그대로 보내면
 * 서버가 400 을 낸다 — 화면에 보이지 않는 값 때문에 등록이 막히는 셈이라 여기서 끊는다.
 *
 * 설명은 `ETC` 가 아니어도 보낸다. 서버가 받아 주고, 사용자가 적어 둔 것을 버릴 이유가 없다.
 */
function toOriginReason(
  values: MaterialFormValues,
): Pick<CreateMaterialRequest, 'originReason' | 'originReasonNote'> {
  if (values.rootOrigin !== 'WASTE' || !values.originReason) return {}
  const note = values.originReasonNote.trim()
  return {
    originReason: values.originReason,
    ...(note ? { originReasonNote: note } : {}),
  }
}

/**
 * 구성을 전송 본문으로.
 *
 * <b>덜 채운 줄은 버린다.</b> "섬유 추가" 를 누르고 아무것도 고르지 않은 빈 줄이 남는
 * 것이 정상적인 사용 흐름이다. 그 줄을 보내면 서버가 400 을 내므로 여기서 걸러낸다.
 * 폼 검증이 빈 줄을 막지만, 사용자가 줄을 추가한 뒤 다른 필드를 고치는 동안에도
 * 제출이 가능해야 한다.
 *
 * 결과가 비면 필드를 아예 넣지 않는다 — 빈 배열과 미입력은 서버에서 같지만,
 * 보내지 않는 편이 "입력하지 않았다" 는 뜻을 정확히 전한다.
 */
function toComposition(values: MaterialFormValues): Pick<CreateMaterialRequest, 'composition'> {
  const rows = values.composition
    .filter((row): row is { fiberType: MaterialFiberType; percentage: number } =>
      Boolean(row.fiberType) && row.percentage !== null)
    .map((row) => ({ fiberType: row.fiberType, percentage: row.percentage }))
  return rows.length > 0 ? { composition: rows } : {}
}

/** 빈 입력을 0 으로 만들지 않는다. "미입력"(null)과 "0 을 입력함" 은 다르게 검증해야 한다. */
function toOptionalNumber(raw: unknown): number | null {
  if (raw === '' || raw === null || raw === undefined) return null
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}
