import { useMemo, useRef, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Warning } from '@phosphor-icons/react'
import { Button, ConfirmModal, ErrorMessage, Input, Select } from '../../../components/common/index.ts'
import { gramToKg, kgToGram } from '../../../utils/quantity.ts'
import { useCreateMaterial } from '../hooks/useCreateMaterial.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useMintMaterialOnChain } from '../hooks/useMintMaterialOnChain.ts'
import { useMyMaterials } from '../../inventory/hooks/useMyMaterials.ts'
import { getMaterial } from '../api/getMaterial.ts'
import type { ChainLinkStatus } from '../../../hooks/useOnChainLink.ts'
import { evidencePlaceholder } from '../services/evidencePlaceholder.ts'
import { uploadEvidence } from '../../evidence/api/uploadEvidence.ts'
import type { EvidenceFile } from '../../evidence/types/evidence.types.ts'
import { WeightPhotoRecognition } from './WeightPhotoRecognition.tsx'
import './material.css'
import { WIRE_UNIT } from '../types/materialQuantity.ts'
import {
  COMPOSITION_MAX_TOTAL,
  FIBER_TYPE_LABEL,
  GRADE_LABEL,
  MATERIAL_FIBER_TYPES,
  MATERIAL_TYPE_LABEL,
  ORIGIN_REASON_LABEL,
  ROOT_ORIGIN_LABEL,
  originReasonRequiresNote,
  type CreateMaterialRequest,
  type CreateMaterialResponse,
  type MaterialDetail,
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

// 종류가 늘 때 이 화면만 고치는 일이 없도록 공통 라벨 표에서 파생한다.
const MATERIAL_TYPE_OPTIONS: readonly { value: MaterialType; label: string }[] =
  (Object.entries(MATERIAL_TYPE_LABEL) as [MaterialType, string][])
    .map(([value, label]) => ({ value, label }))

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
  SIGNING: { percent: 20, label: '지갑에서 요청을 확인하는 중' },
  PENDING: { percent: 55, label: '소재 이력을 저장하는 중' },
  CONFIRMED: { percent: 80, label: '저장 결과를 확인하는 중' },
  LINKING: { percent: 80, label: '저장 결과를 확인하는 중' },
  LINKED: { percent: 100, label: '등록 완료' },
}

/**
 * 화면 위의 진행 표시.
 *
 * <b>네 구획은 모두 한 화면에 펼쳐져 있다.</b> 이 줄은 단계를 강제하는 마법사가 아니라
 * "얼마나 남았는지" 를 알려주고 눌러서 건너뛰게 하는 이정표다. 12개 입력을 그냥 세로로
 * 쌓으면 어디까지 했는지 알 수 없어 생긴 장치다.
 */
const STEPS: readonly {
  title: string
  hint: string
  /** 이 구획을 벗어날 때 검증할 필드. 비면 검증 없이 넘어간다 */
  fields: readonly (keyof MaterialFormValues)[]
}[] = [
  { title: '기본 정보', hint: '유형 · 출처 · 소재명', fields: ['materialType', 'rootOrigin', 'name'] },
  { title: '수량 · 규격', hint: '수량 필수', fields: ['quantityKg', 'lengthMm', 'widthMm', 'description'] },
  { title: '구성 · 발생', hint: '모두 선택', fields: ['originReason', 'originReasonNote', 'composition'] },
  { title: '등록 확인', hint: '최종 확인', fields: [] },
]

/**
 * 어느 구획에 있는 필드인가.
 *
 * 제출이 검증에 걸렸을 때 <b>그 필드가 있는 구획으로 데려가기</b> 위해 쓴다. 등록 버튼은
 * 오른쪽에 늘 떠 있어서 1구획에서도 누를 수 있는데, 비어 있는 필수 항목이 2구획에 있으면
 * 그 오류 메시지는 화면에 없는 자리에 그려진다 — 아무 일도 안 일어난 것처럼 보인다.
 */
function stepOfField(field: string): number {
  const index = STEPS.findIndex((step) => step.fields.some((name) => field.startsWith(name)))
  return index === -1 ? 0 : index
}

/**
 * 요약 한 줄.
 *
 * 값이 없으면 회색 대체 문구를 둔다 — 줄을 통째로 빼면 <b>무엇이 비었는지</b> 보이지 않고,
 * 다 채운 화면과 절반만 채운 화면이 똑같이 짧아진다.
 */
function SummaryRow({
  label,
  value,
  fallback = '—',
}: {
  label: string
  value: string | undefined
  fallback?: string
}) {
  const filled = value !== undefined && value !== ''
  return (
    <div>
      <dt>{label}</dt>
      <dd className={filled ? undefined : 'mat-summary__empty'}>{filled ? value : fallback}</dd>
    </div>
  )
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
 * 저울 사진은 OCR 로 수량 입력을 돕고, 소재 생성 뒤 중량 증빙으로 업로드한다.
 * 업로드된 파일 해시를 온체인 발행에 사용하므로 선택 사진·증빙은 재시도 상태에 보존한다.
 */
export function MaterialForm({ onCreated }: MaterialFormProps) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    setValue,
    trigger,
    setError,
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
  const materialType = useWatch({ control, name: 'materialType' })
  // 오른쪽 요약이 입력과 함께 움직여야 하므로 값을 구독한다. watch() 가 아니라
  // useWatch() 인 이유는 위와 같다 — watch() 가 주는 함수는 렌더마다 새로 만들어진다.
  const name = useWatch({ control, name: 'name' })
  const quantityKg = useWatch({ control, name: 'quantityKg' })
  const grade = useWatch({ control, name: 'grade' })
  const lengthMm = useWatch({ control, name: 'lengthMm' })
  const widthMm = useWatch({ control, name: 'widthMm' })

  const { fields, append, remove } = useFieldArray({ control, name: 'composition' })

  /**
   * 지금 보고 있는 구획 (0부터).
   *
   * <b>한 번에 한 구획만 보여준다.</b> 입력이 12개라 한 화면에 쌓으면 어디까지 했는지
   * 알 수 없었다. 감추는 것(접기)과는 다르다 — 감추면 선택 항목을 아예 안 보고 지나가지만,
   * 이렇게 나누면 모든 항목을 거쳐 가므로 오히려 채워질 확률이 높다.
   *
   * 값은 구획을 넘어가도 남는다. react-hook-form 은 `shouldUnregister` 가 기본 false 라
   * 필드가 화면에서 사라져도 값을 들고 있다.
   */
  const [step, setStep] = useState(0)
  // 한 번이라도 지나온 구획은 다시 눌러 갈 수 있다. 앞으로 건너뛰는 것은 막는다 —
  // 검증을 통과하지 않은 채 넘어가면 마지막에 몰아서 틀린 것을 알게 된다.
  const [visited, setVisited] = useState(0)

  async function goToStep(next: number) {
    if (next <= visited) {
      setStep(next)
      return
    }
    // 앞으로 갈 때만 지금 구획을 검증한다.
    const ok = await trigger(STEPS[step].fields as never[])
    if (!ok) return
    setStep(next)
    setVisited((max) => Math.max(max, next))
  }

  const isWaste = rootOrigin === 'WASTE'
  // 소재 유형 ENUM 에 자유 입력 값을 보낼 수 없다(서버 400). '기타' 를 고르면
  // 실제 종류를 설명에 적게 해 유형은 ENUM 그대로, 값은 설명으로 전달한다.
  const isEtcMaterialType = materialType === 'ETC'
  const compositionTotal = (composition ?? []).reduce(
    (sum, row) => sum + (row?.percentage ?? 0),
    0,
  )

  // 요약에 넣을 한 줄. 섬유를 고르고 비율을 아직 안 적은 줄은 건너뛴다 —
  // "면 %" 처럼 반쪽만 찍히면 잘못 입력한 것처럼 읽힌다.
  const compositionSummary = (composition ?? [])
    .filter((row) => row?.fiberType && row.percentage !== null)
    .map((row) => `${FIBER_TYPE_LABEL[row.fiberType as MaterialFiberType]} ${row.percentage}%`)
    .join(' · ')

  const { mutateAsync, isPending, isError, error } = useCreateMaterial()

  // 발행은 등록자 지갑이 서명한다 — 서버에는 개인키가 없다.
  const wallet = useWallet()
  const mint = useMintMaterialOnChain()
  const [pendingRegistration, setPendingRegistration] = useState<{
    created: CreateMaterialResponse
    request: CreateMaterialRequest
    weightPhoto: File | null
    weightEvidence: EvidenceFile | null
  } | null>(null)
  const [weightPhoto, setWeightPhoto] = useState<File | null>(null)
  const [weightPhotoResetKey, setWeightPhotoResetKey] = useState(0)
  const [evidenceUploadError, setEvidenceUploadError] = useState<string | null>(null)
  const [isUploadingEvidence, setIsUploadingEvidence] = useState(false)
  // 제출을 눌러 봤는데 지갑이 없었다. 처음부터 경고를 띄우지 않는 이유는, 지갑 복원이
  // 비동기라 화면이 뜨자마자 잠깐 `null` 이기 때문이다. 그때 경고부터 보이면 정상적으로
  // 연결된 사용자에게도 한 번 번쩍인다.
  const [walletRequired, setWalletRequired] = useState(false)

  // ── 이전 기록 불러오기 (S15P21E104-mypage) ──
  //
  // 이 화면에 맞는 "가장 최근 소재" 조회 API 가 없다. 보유 소재 목록(`useMyMaterials`)에서
  // materialId 가 가장 큰 항목을 최신으로 보고, 그 상세(`getMaterial`)를 받아 폼을 채운다.
  const { data: myMaterials } = useMyMaterials()
  const latestMaterialId = useMemo(() => {
    if (!myMaterials || myMaterials.length === 0) return null
    return myMaterials.reduce((latest, item) =>
      item.materialId > latest.materialId ? item : latest,
    ).materialId
  }, [myMaterials])
  const [isLoadingPrevious, setIsLoadingPrevious] = useState(false)
  const [loadPreviousError, setLoadPreviousError] = useState<string | null>(null)
  // 불러온 직후의 폼 값 스냅샷. 제출 시점 값과 비교해 "하나도 안 바꿨는지" 를
  // 판단한다. 불러오기를 아예 안 했으면 `null` 로 남아 그 검사 자체를 건너뛴다.
  const previousSnapshotRef = useRef<string | null>(null)
  const [pendingDuplicateConfirm, setPendingDuplicateConfirm] = useState<MaterialFormValues | null>(
    null,
  )

  const handleLoadPrevious = async () => {
    if (latestMaterialId === null) {
      setLoadPreviousError('불러올 이전 기록이 없습니다.')
      return
    }
    setLoadPreviousError(null)
    setIsLoadingPrevious(true)
    try {
      const detail = await getMaterial(latestMaterialId)
      const values = toFormValues(detail)
      reset(values)
      previousSnapshotRef.current = JSON.stringify(values)
    } catch {
      setLoadPreviousError('이전 기록을 불러오지 못했습니다.')
    } finally {
      setIsLoadingPrevious(false)
    }
  }

  // 지갑 확인 → 구성 비율 검증 → 등록 → 온체인 발행까지의 본 로직. 중복 확인
  // 모달을 넣기 전부터 있던 로직 그대로이며, 최소 침습을 위해 함수로만 뽑아냈다 —
  // `onSubmit` 과 모달의 `onConfirm` 양쪽에서 재사용한다.
  const registerMaterial = async (values: MaterialFormValues) => {
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

    // 각 섬유의 비율은 개별로는 100% 를 넘지 않아도(예: 60% + 60%) 합계는 넘을 수
    // 있다. 버튼도 막아 두지만(disabled), 그 상태를 우회해 제출이 들어와도 여기서
    // 다시 막는다 — 서버가 합계를 검증하지 않아 여기서 막지 않으면 그대로 등록된다.
    if (compositionTotal > COMPOSITION_MAX_TOTAL) return

    try {
      setEvidenceUploadError(null)

      // 생성 성공 시점에 요청과 선택 사진을 함께 고정한다. 이후 업로드나 발행이 실패해도
      // 새 소재를 만들거나 다른 사진으로 바꾸지 않고 정확히 그 등록을 이어서 수행한다.
      let registration = pendingRegistration
      if (registration === null) {
        const request = toRequest(values)
        const created = await mutateAsync(request)
        registration = {
          created,
          request,
          weightPhoto,
          weightEvidence: null,
        }
        setPendingRegistration(registration)
      }

      let weightEvidence = registration.weightEvidence
      if (registration.weightPhoto !== null && weightEvidence === null) {
        setIsUploadingEvidence(true)
        try {
          const files = await uploadEvidence({
            referenceType: 'MATERIAL',
            referenceId: registration.created.materialId,
            evidenceType: 'WEIGHT_PHOTO',
            files: [registration.weightPhoto],
          })
          if (files.length === 0) {
            setEvidenceUploadError('저울 사진 증빙 업로드에 실패했습니다. 다시 시도해 주세요.')
            return
          }
          weightEvidence = files[0]
          registration = { ...registration, weightEvidence }
          setPendingRegistration(registration)
        } catch {
          setEvidenceUploadError('저울 사진 증빙 업로드에 실패했습니다. 다시 시도해 주세요.')
          return
        } finally {
          setIsUploadingEvidence(false)
        }
      }

      const linked = await mint.mint({
        materialId: registration.created.materialId,
        to: wallet.address,
        amount: registration.request.quantity,
        rootOrigin: registration.request.rootOrigin,
        metadataUri: `saeroi://material/${registration.created.materialCode}`,
        evidenceHash:
          weightEvidence?.fileHash ?? evidencePlaceholder(registration.created.materialCode),
      })

      if (linked) {
        setPendingRegistration(null)
        setWeightPhoto(null)
        setWeightPhotoResetKey((key) => key + 1)
        setEvidenceUploadError(null)
        reset()
        previousSnapshotRef.current = null
        onCreated?.(registration.created.materialId)
      }
    } catch {
      // API와 지갑 오류는 각 훅의 오류 상태로 화면에 표시한다.
    }
  }

  // "이전 기록 불러오기" 를 눌렀고, 그 뒤로 값을 하나도 안 바꿨다면 먼저 확인을
  // 받는다. 스냅샷이 없으면(불러오기를 안 했으면) 이 검사 자체를 건너뛰고
  // 지금까지와 똑같이 바로 등록으로 진행한다.
  //
  // `handleSubmit(callback)` 을 렌더 중에 바로 호출해 저장해 두지 않는다 —
  // 그 callback 이 ref(`previousSnapshotRef`)를 읽는데, eslint-plugin-react-hooks
  // 의 refs 규칙이 "렌더 중 호출되는 함수에 ref 를 읽는 콜백을 넘긴다" 로 보고
  // 오탐(false positive)을 낸다. `<form onSubmit>` 안에서 그때그때
  // `handleSubmit(...)` 을 호출하면 렌더 시점에는 아무 함수도 만들어지지 않는다.
  /**
   * <b>구획을 넘나드는 규칙은 여기서 검사한다.</b>
   *
   * 필드에 붙인 `validate` 는 그 입력칸이 화면에 있을 때만 돈다 — 지금 폼은 한 번에 한
   * 구획만 그리므로, 다른 구획의 필드는 언마운트돼 검증에서 빠진다. 실제로 1구획에서
   * 유형을 '기타' 로 바꾸고 등록하면 2구획에 있는 설명 칸의 규칙이 돌지 않아,
   * <b>설명 없는 ETC 소재가 그대로 저장됐다</b>.
   *
   * 그래서 제출 값 전체를 손에 쥔 이 자리에서 다시 본다. 오류를 붙이고 그 필드가 있는
   * 구획으로 데려간다 — 메시지만 세워 두고 화면을 안 옮기면 아무 일도 안 일어난 것처럼 보인다.
   */
  function checkAcrossSteps(values: MaterialFormValues): boolean {
    // ENUM 에 없는 소재는 '기타' 로만 보낼 수 있다. 실제 종류가 설명에도 없으면
    // 등록 후 사람이 봐도 무슨 소재인지 알 수 없다.
    if (values.materialType === 'ETC' && values.description.trim().length === 0) {
      setError('description', { message: '기타를 선택하면 설명이 필요합니다.' })
      const target = stepOfField('description')
      setStep(target)
      setVisited((max) => Math.max(max, target))
      return false
    }

    // ETC 는 값 자체가 뜻을 담지 않아 설명이 없으면 분류가 무의미해진다.
    if (values.originReason === 'ETC' && values.originReasonNote.trim().length === 0) {
      setError('originReasonNote', { message: '기타를 선택하면 설명이 필요합니다.' })
      const target = stepOfField('originReasonNote')
      setStep(target)
      setVisited((max) => Math.max(max, target))
      return false
    }

    return true
  }

  const submitValues = async (values: MaterialFormValues) => {
    if (!checkAcrossSteps(values)) return
    if (previousSnapshotRef.current !== null && JSON.stringify(values) === previousSnapshotRef.current) {
      setPendingDuplicateConfirm(values)
      return
    }
    await registerMaterial(values)
  }

  const handleConfirmDuplicate = () => {
    const values = pendingDuplicateConfirm
    setPendingDuplicateConfirm(null)
    if (values) void registerMaterial(values)
  }

  return (
    <form
      className="material-form"
      onSubmit={(event) => {
        void handleSubmit(submitValues, (invalid) => {
          // 등록 버튼은 오른쪽에 늘 떠 있어 1구획에서도 눌린다. 비어 있는 필수 항목이
          // 다른 구획에 있으면 그 오류는 화면에 없는 자리에 그려져, 사용자에게는
          // 아무 일도 안 일어난 것처럼 보인다. 그래서 그 구획으로 데려간다.
          const first = Object.keys(invalid)[0]
          if (first === undefined) return
          const target = stepOfField(first)
          setStep(target)
          setVisited((max) => Math.max(max, target))
        })(event)
      }}
      noValidate
    >
      {/*
        <b>구획 하나가 아니라 폼 전체를 채우는 동작이라 맨 위에 둔다.</b>

        예전에는 1구획 안에 있었는데, 하는 일은 네 구획을 통째로 덮어쓰는 것이다 —
        1구획만 보고 있는 사용자는 그 버튼이 어디까지 바꾸는지 알 수 없었다. 무엇을
        바꾸는지 옆에 적어 두는 것도 같은 이유다.
      */}
      <div className="mat-prefill">
        <span className="mat-prefill__note">
          직전에 등록한 소재와 비슷하다면 값을 불러와 고쳐 쓸 수 있습니다.
        </span>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          loading={isLoadingPrevious}
          // 보유 소재가 하나도 없으면 불러올 대상이 없다.
          disabled={latestMaterialId === null}
          onClick={handleLoadPrevious}
        >
          이전 기록 불러오기
        </Button>
      </div>
      {loadPreviousError && <ErrorMessage error={loadPreviousError} />}

      {/*
        진행 표시.

        <b>강제로 밟는 단계가 아니다.</b> 네 구획이 한 화면에 모두 펼쳐져 있고, 이 줄은
        "지금 어디쯤" 만 알려준다. 필수 항목이 넷뿐이라(유형·출처·소재명·수량) 단계를
        강제하면 30초면 끝날 일이 네 화면이 된다.
      */}
      <ol className="mat-steps">
        {STEPS.map((item, index) => (
          <li
            key={item.title}
            className={
              index === step ? 'is-current' : index < visited ? 'is-done' : undefined
            }
          >
            <button
              type="button"
              // 지나온 구획만 누를 수 있다. 안 지난 곳을 눌러 건너뛰면 검증을 통과하지
              // 않은 채 넘어가고, 틀린 것을 마지막에 몰아서 알게 된다.
              disabled={index > visited}
              aria-current={index === step ? 'step' : undefined}
              onClick={() => void goToStep(index)}
            >
              <span className="mat-steps__no">{index + 1}</span>
              <span>
                <strong>{item.title}</strong>
                <small>{index === step ? '작성 중' : item.hint}</small>
              </span>
            </button>
          </li>
        ))}
      </ol>

      <div className="mat-layout">
        <div className="mat-main">
          {step === 0 && (
          <section id="mat-step-1" className="console-panel mat-section">
            <div className="mat-section__head">
              <h2>
                <span className="mat-section__dot" aria-hidden="true" />1. 기본 정보
              </h2>
              <span className="console__tag">identification</span>
            </div>

      {/* 짧은 필드는 한 줄에 여러 개 — 선택지 하나하나가 줄 하나를 다 차지하면
          폼이 필요 이상으로 길어진다. 소재명 · 설명처럼 값이 길어질 수 있는
          입력만 그리드 밖에 두고 전체 폭을 쓴다. */}
      <div className="material-form__row">
        <Select
          label="소재 유형"
          placeholder="선택해 주세요"
          options={MATERIAL_TYPE_OPTIONS}
          error={errors.materialType?.message}
          {...register('materialType', { required: '소재 유형을 선택해 주세요.' })}
        />

        <Select
          label="출처"
          options={ROOT_ORIGIN_OPTIONS}
          error={errors.rootOrigin?.message}
          {...register('rootOrigin', { required: '출처를 선택해 주세요.' })}
        />

        <Select
          label="등급 (선택)"
          placeholder="선택 안 함"
          options={GRADE_OPTIONS}
          error={errors.grade?.message}
          {...register('grade')}
        />
      </div>

      <Input
        label="소재명"
        placeholder="폐데님 원단"
        error={errors.name?.message}
        {...register('name', {
          required: '소재명을 입력해 주세요.',
          // required 는 빈 문자열만 막는다 — 공백만 넣으면 truthy 라 그대로 통과했고
          // 오류 하나 없이 다음 단계로 넘어갔다 (2026-09-21 확인).
          // 가입 폼들이 이미 쓰는 방식(trim 후 길이 확인)과 맞춘다.
          validate: (value) => value.trim().length > 0 || '소재명을 입력해 주세요.',
          maxLength: { value: 200, message: '소재명은 200자를 넘을 수 없습니다.' },
        })}
      />
          </section>
          )}

          {step === 1 && (
          <section id="mat-step-2" className="console-panel mat-section">
            <div className="mat-section__head">
              <h2>
                <span className="mat-section__dot" aria-hidden="true" />2. 수량 · 규격
              </h2>
              <span className="console__tag">metrics</span>
            </div>

      <div className="material-form__row">
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
      </div>

      {pendingRegistration ? (
        <section className="weight-recognition" aria-labelledby="weight-recognition-title">
          <div className="weight-recognition__heading">
            <div>
              <h3 id="weight-recognition-title">저울 사진으로 무게 인식</h3>
              <p>이미 생성된 소재의 증빙은 재시도 중에 변경할 수 없습니다.</p>
            </div>
          </div>
          <p>{pendingRegistration.weightPhoto?.name ?? '선택된 저울 사진 없음'}</p>
        </section>
      ) : (
        <WeightPhotoRecognition
          key={weightPhotoResetKey}
          onFileChange={setWeightPhoto}
          onApply={(weightGram) => {
            setValue('quantityKg', gramToKg(weightGram), {
              shouldValidate: true,
              shouldDirty: true,
            })
          }}
        />
      )}

      <Input
        label={isEtcMaterialType ? '설명 (기타 소재의 실제 종류를 적어주세요)' : '설명 (선택)'}
        placeholder={isEtcMaterialType ? '예: 대나무 섬유' : '봉제 공장에서 회수한 자투리 원단'}
        error={errors.description?.message}
        {...register('description', {
          // ENUM 목록에 없는 소재는 '기타' 로만 보낼 수 있다 — 실제 종류가 어디에도
          // 안 남으면 등록 후 사람이 봐도 무슨 소재인지 알 수 없다.
          //
          // **바깥의 `isEtcMaterialType` 을 쓰지 않는다.** 이 입력칸은 2구획에 있어서
          // 1구획을 보는 동안에는 화면에 없고, 그때 register 가 다시 불리지 않아
          // 클로저가 낡은 값을 붙든다. 1구획에서 유형을 '기타' 로 바꾼 뒤 등록하면
          // 검증이 통과해 설명 없는 ETC 소재가 그대로 저장됐다.
          //
          // validate 의 두 번째 인자는 **제출 시점의 폼 값 전체**다. 여기서 읽으면
          // 어느 구획을 보고 있든 같은 판단이 나온다.
          validate: (value, formValues) =>
            formValues.materialType !== 'ETC' ||
            value.trim().length > 0 ||
            '기타를 선택하면 설명이 필요합니다.',
        })}
      />

          </section>
          )}

          {step === 2 && (
          <section id="mat-step-3" className="console-panel mat-section">
            <div className="mat-section__head">
              <h2>
                <span className="mat-section__dot" aria-hidden="true" />3. 구성 · 발생 사유
              </h2>
              <span className="console__tag">composition</span>
            </div>

            {/*
              <b>전부 선택 입력이지만 접지 않는다.</b> 접으면 채워지는 비율이 떨어지는데,
              이 서비스는 소재 정보가 얇으면 브랜드가 고를 근거도, 최종 소비자에게 보여줄
              이력도 얇아진다. 실제로 지금도 등급이 빈 소재가 적지 않다 — 펼쳐 둔 채로도
              그렇다. 감추면 더 늘어난다.
            */}
            <p className="mat-section__note">
              모두 선택 입력입니다. 다만 채워 둘수록 구매자가 판단할 근거가 늘어납니다.
            </p>

      {/*
        발생 사유는 출처가 WASTE 일 때만 보인다. 신소재에 사유를 보내면 서버가 400 을
        내므로, 칸을 감추는 것이 오류 메시지를 다듬는 것보다 낫다.
      */}
      {isWaste && (
        <div className="material-form__row">
          <Select
            label="발생 사유 (선택)"
            placeholder="선택 안 함"
            options={ORIGIN_REASON_OPTIONS}
            error={errors.originReason?.message}
            {...register('originReason')}
          />

          {/* ETC 는 값 자체가 뜻을 담지 않아 설명이 없으면 분류가 무의미해진다. */}
          {originReasonRequiresNote(originReason as MaterialOriginReason) && (
            <Input
              label="발생 사유 설명"
              placeholder="행사 후 남은 배너 원단"
              error={errors.originReasonNote?.message}
              {...register('originReasonNote', {
                // 위와 같은 이유로 바깥 값이 아니라 폼 값으로 판단한다. 이 칸은
                // 발생 사유가 ETC 일 때만 그려지므로 클로저가 낡기 쉽다.
                validate: (value, formValues) =>
                  formValues.originReason !== 'ETC' ||
                  value.trim().length > 0 ||
                  '기타를 선택하면 설명이 필요합니다.',
                maxLength: { value: 200, message: '설명은 200자를 넘을 수 없습니다.' },
              })}
            />
          )}
        </div>
      )}

      <fieldset className="material-form__composition">
        <legend>구성 (선택)</legend>
        {/*
          합계 100 을 요구하지 않는다. "면 60% 는 확실하고 나머지는 모른다" 가 흔하고,
          강제하면 사용자가 "기타 40%" 를 지어낸다. 대신 합계를 보여줘 스스로 판단하게 한다.
        */}
        <p
          aria-live="polite"
          role={compositionTotal > COMPOSITION_MAX_TOTAL ? 'alert' : undefined}
          className={
            compositionTotal > COMPOSITION_MAX_TOTAL
              ? 'material-form__composition-total material-form__composition-total--error'
              : 'material-form__composition-total'
          }
        >
          합계 {compositionTotal.toLocaleString('ko-KR')}%
          {compositionTotal > COMPOSITION_MAX_TOTAL && ' — 100%를 넘을 수 없습니다. 등록하려면 비율을 줄여주세요.'}
          {compositionTotal > 0 && compositionTotal < COMPOSITION_MAX_TOTAL && ' (나머지 미확인)'}
        </p>

        {fields.map((field, index) => (
          <div key={field.id} className="material-form__composition-row">
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
          </section>
          )}

          {step === 3 && (
            <section id="mat-step-4" className="console-panel mat-section">
              <div className="mat-section__head">
                <h2>
                  <span className="mat-section__dot" aria-hidden="true" />4. 등록 확인
                </h2>
                <span className="console__tag">확인</span>
              </div>
              <p className="mat-section__note">
                오른쪽 요약에서 값을 확인한 뒤 등록하세요. 지갑에서 요청을 확인하면
                이력 기록이 완료됩니다.
              </p>

              {/*
                여기서만 진행률을 보여준다. 등록은 서버 저장 → 지갑 서명 → 블록 확정 →
                서버 기록으로 이어지는데, 그 사이 화면이 조용하면 사용자는 멈췄다고
                오해하고 새로고침하거나 다시 누른다.
              */}
              {MINT_STAGE[mint.status] && (
                <div className="material-form__mint-progress" role="status">
                  <p>소재 이력을 기록하는 중입니다. 완료되면 보유 소재 목록으로 이동합니다.</p>
                  <progress max={100} value={MINT_STAGE[mint.status]?.percent} />
                  <span>
                    {MINT_STAGE[mint.status]?.label} ({MINT_STAGE[mint.status]?.percent}%)
                  </span>
                </div>
              )}
            </section>
          )}

          {/* 구획 이동. 제출은 오른쪽 기둥이 맡는다 — 필수를 다 채운 사람은
              끝까지 넘기지 않고 바로 등록할 수 있어야 한다. */}
          <div className="mat-nav">
            <Button
              type="button"
              variant="secondary"
              disabled={step === 0}
              onClick={() => void goToStep(step - 1)}
            >
              이전
            </Button>
            <span className="mat-nav__where console__num">
              {step + 1} / {STEPS.length}
            </span>
            {step < STEPS.length - 1 && (
              <Button type="button" onClick={() => void goToStep(step + 1)}>
                다음: {STEPS[step + 1].title} →
              </Button>
            )}
          </div>
        </div>

        {/*
          오른쪽 기둥. 스크롤을 따라온다.

          왼쪽이 길어질 수밖에 없는 폼이라(선택 항목을 감추지 않기로 했다) 끝까지 내려가야
          등록 버튼이 나오면 안 된다. 그리고 <b>무엇을 채웠고 무엇이 비었는지</b>가 입력하는
          내내 보여야, 다 쓴 뒤에 위로 되짚어 올라가는 일이 없다.
        */}
        <aside className="mat-side">
          <section className="console-panel mat-summary">
            <div className="mat-section__head">
              <h2>실시간 요약</h2>
              <span className="console-badge console-badge--flat">draft</span>
            </div>

            <dl className="mat-summary__list">
              <SummaryRow label="소재명" value={name?.trim()} />
              <SummaryRow
                label="유형 · 출처"
                value={
                  materialType
                    ? `${MATERIAL_TYPE_LABEL[materialType as MaterialType]} · ${ROOT_ORIGIN_LABEL[rootOrigin]}`
                    : ''
                }
              />
              <SummaryRow
                label="품질 등급"
                value={grade ? `${grade}등급` : ''}
                fallback="미지정"
              />
            </dl>

            {/* 이 화면에서 가장 중요한 숫자. 요약 안에서도 따로 띄운다. */}
            <div className="mat-summary__hero">
              <span>등록 수량</span>
              <strong className="console__num">
                {Number.isFinite(quantityKg) && quantityKg > 0 ? quantityKg.toLocaleString('ko-KR') : '—'}
                <small>kg</small>
              </strong>
            </div>

            <dl className="mat-summary__list">
              {/* 규격은 비정형 소재에 아예 없는 값이라, 비었으면 줄째 숨긴다.
                  "—" 로 남겨 두면 안 채운 것처럼 읽힌다. */}
              {(lengthMm !== null || widthMm !== null) && (
                <SummaryRow
                  label="규격 (L × W)"
                  value={`${(lengthMm ?? 0).toLocaleString('ko-KR')} × ${(widthMm ?? 0).toLocaleString('ko-KR')} mm`}
                />
              )}
              <SummaryRow label="섬유 구성" value={compositionSummary} />
              {isWaste && (
                <SummaryRow
                  label="발생 사유"
                  value={originReason ? ORIGIN_REASON_LABEL[originReason as MaterialOriginReason] : ''}
                />
              )}
            </dl>
          </section>

          {/*
            서명할 지갑. 등록 전에 사용자가 확인해야 하는 마지막 값이다 — 여러 지갑을
            오가는 사용자가 엉뚱한 계정으로 서명하면 되돌릴 수 없다.
          */}
          {/*
            제출을 시도했는데 지갑이 없으면 이 패널이 <b>소리를 낸다</b>(role="alert").
            평소에는 조용히 상태만 보여주다가, 막힌 순간에만 보조 기술이 읽고 색이 바뀐다 —
            늘 경고 모양이면 아무도 보지 않는다.
          */}
          <section
            className={`console-panel mat-wallet${walletRequired && wallet.address === null ? ' mat-wallet--required' : ''}`}
            role={walletRequired && wallet.address === null ? 'alert' : undefined}
          >
            <div className="mat-section__head">
              <h2>등록에 사용할 지갑</h2>
              {wallet.address === null ? (
                <span className="console-badge console-badge--wait">연결 필요</span>
              ) : (
                <span className="console-badge console-badge--ok">
                  <span className="console-badge__dot" aria-hidden="true" />
                  연결됨
                </span>
              )}
            </div>

            {wallet.address === null ? (
              <>
                <p className="mat-wallet__note">
                  <strong>지갑을 먼저 연결해 주세요.</strong> 소재를 등록하면 주요 정보가
                  변경할 수 없는 이력으로 함께 저장됩니다.
                </p>
                {/*
                  <b>다른 주소에서 이미 연결해 둔 사용자에게 필요한 설명이다.</b>
                  "지갑을 연결해 주세요" 만으로는 왜 또 연결해야 하는지 알 수 없다 —
                  MetaMask 의 연결 승인은 주소(origin)마다 따로 저장된다. 제출한 뒤에
                  알려 주면 이미 폼을 다 채운 뒤라, 들어오자마자 보이게 둔다.
                */}
                <p className="mat-wallet__note">
                  다른 주소(예: <code>localhost</code>)에서 연결해 두었더라도 지금
                  주소에서는 <b>다시 승인</b>해야 합니다.
                </p>
                <Button type="button" variant="secondary" size="sm" onClick={wallet.connect}>
                  지갑 연결
                </Button>
              </>
            ) : (
              <>
                <p className="mat-wallet__address console__num">{wallet.address}</p>
                <p className="mat-wallet__note">이 지갑으로 등록을 확인합니다.</p>
              </>
            )}
          </section>

          {/*
            <b>되돌릴 수 없다는 사실을 등록 전에 말한다.</b> 소재를 취소하거나 지우는 기능이
            지금 이 서비스에 없다 — 잘못 등록하면 보유 목록에 영원히 남는다. 겁주지 않되
            사실은 적는다.
          */}
          <p className="mat-warn" role="note">
            <Warning aria-hidden="true" weight="fill" />
            <span>
              <strong>등록 후에는 되돌릴 수 없습니다.</strong>
              등록 즉시 고유 코드가 발급되며, 소재를 취소·삭제하는 기능은 없습니다. 수량과
              규격을 다시 확인해 주세요.
            </span>
          </p>

          {isError && <ErrorMessage error={error} />}
          {evidenceUploadError && <ErrorMessage error={evidenceUploadError} />}
          {mint.error && !mint.isRejected && <ErrorMessage error={mint.error} />}
          {isUploadingEvidence && (
            <p role="status" aria-live="polite">저울 사진 증빙을 업로드하는 중입니다…</p>
          )}

          <Button
            type="submit"
            fullWidth
            loading={isPending || isUploadingEvidence || mint.isBusy}
            disabled={compositionTotal > COMPOSITION_MAX_TOTAL}
          >
            {pendingRegistration && !mint.isBusy ? '소재 등록 완료하기' : '소재 등록'}
          </Button>
        </aside>
      </div>

      <ConfirmModal
        open={pendingDuplicateConfirm !== null}
        title="소재 중복 확인"
        message="이전과 완전히 동일한 소재입니다. 맞습니까?"
        onConfirm={handleConfirmDuplicate}
        onClose={() => setPendingDuplicateConfirm(null)}
      />
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
 * 소재 상세(`MaterialDetail`) → 폼 값.
 *
 * `toRequest()` 의 반대 방향이다. "이전 기록 불러오기" 가 받아온 상세 응답을 폼에
 * 채우기 위해 쓴다. 서버가 `null` 을 주는 선택 필드(`grade`·`originReason`)는
 * 폼의 "선택 안 함" 표현인 `''` 로 바꾼다.
 */
function toFormValues(detail: MaterialDetail): MaterialFormValues {
  return {
    materialType: detail.materialType,
    rootOrigin: detail.rootOrigin,
    name: detail.name,
    quantityKg: gramToKg(detail.totalQuantity),
    grade: detail.grade ?? '',
    description: detail.description ?? '',
    lengthMm: detail.lengthMm,
    widthMm: detail.widthMm,
    originReason: detail.originReason ?? '',
    originReasonNote: detail.originReasonNote ?? '',
    composition: detail.composition.map((item) => ({
      fiberType: item.fiberType as MaterialFiberType,
      percentage: item.percentage,
    })),
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
