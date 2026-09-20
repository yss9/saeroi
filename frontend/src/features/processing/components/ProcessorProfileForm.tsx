import { CheckCircle, Copy, ImageSquare, MagnifyingGlass, MapPin, Plus, Trash, X } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { FileUploader } from '../../../components/common/FileUploader.tsx'
import { useCompanyProfile } from '../../company/hooks/useCompanyProfile.ts'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import { toErrorMessage } from '../../../utils/errors.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import { useProcessorProfile } from '../hooks/useProcessorProfile.ts'
import { MATERIAL_TYPE_LABEL, type MaterialType, type ProcessorCapability } from '../types/processorProfile.types.ts'
import './processor-profile.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
const processTypes = Object.keys(PROCESS_TYPE_LABEL) as ProcessType[]
const emptyCapability = (materialType: MaterialType = 'DENIM', processType: ProcessType = 'CLEANING'): ProcessorCapability => ({ materialType, processType, dailyCapacity: 0, pricePerKg: 0 })
const toKg = (gram: number) => Math.round(gram / 100) / 10
const capabilityKey = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${materialType}:${processType}`
const capabilityLabel = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${MATERIAL_TYPE_LABEL[materialType]} · ${PROCESS_TYPE_LABEL[processType]}`

/**
 * 조건 하나를 적는 창.
 *
 * 가공사가 적는 것은 둘뿐이다 — **하루에 몇 kg 을 처리하는가**, **kg 당 얼마인가**. 그 둘로
 * 브랜드 화면의 예상 금액(단가 × 수량)과 소요일(⌈수량 ÷ 하루 처리량⌉)이 나온다.
 */
interface ConditionEditor {
  mode: 'add' | 'edit'
  index: number | null
  capability: ProcessorCapability
  /** kg 단위 입력값. 서버로는 g 로 바꿔 보낸다 */
  dailyCapacityKg: string
  pricePerKg: string
}

function conditionError(dailyCapacityKg: string, pricePerKg: string) {
  const capacity = Number(dailyCapacityKg)
  const price = Number(pricePerKg)
  // 하루 처리량은 소요일의 분모라 0 일 수 없다. 단가는 0 을 받는다 — 무상 가공이 있다.
  if (!Number.isFinite(capacity) || capacity <= 0) return '하루 처리량을 0보다 크게 입력해 주세요.'
  if (!Number.isInteger(price) || price < 0) return 'kg 당 단가를 원 단위 정수로 입력해 주세요.'
  return ''
}

export function ProcessorProfileForm() {
  const data = useProcessorProfile()
  const company = useCompanyProfile()
  const initialized = useRef(false)
  const editorTitleRef = useRef<HTMLHeadingElement>(null)
  const [region, setRegion] = useState('')
  const [capabilities, setCapabilities] = useState<ProcessorCapability[]>([])
  // 소재도 공정처럼 체크박스다 — 하나만 고르게 하면 "데님과 면을 같은 조건으로" 를
  // 적으려고 같은 일을 두 번 해야 한다.
  const [materialFilters, setMaterialFilters] = useState<MaterialType[]>([])
  const [processFilters, setProcessFilters] = useState<ProcessType[]>([])
  const [editor, setEditor] = useState<ConditionEditor | null>(null)
  const [editorError, setEditorError] = useState('')
  const [saveNotice, setSaveNotice] = useState('')
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const loading = data.profile.isPending || data.capabilities.isPending || company.isPending
  const loadError = data.profile.isError || data.capabilities.isError || company.isError
  const editorOpen = editor !== null
  const profileImageUrl = useObjectUrl(data.image.data)

  useEffect(() => {
    if (initialized.current || loading || loadError) return
    initialized.current = true
    setRegion(data.profile.data?.region ?? company.profile?.address ?? '')
    setCapabilities(data.capabilities.data ?? [])
  }, [company.profile?.address, data.capabilities.data, data.profile.data, loadError, loading])

  useEffect(() => {
    if (!editorOpen) return
    editorTitleRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setEditor(null)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [editorOpen])

  function toggleProcessFilter(processType: ProcessType) {
    setProcessFilters(current => current.includes(processType)
      ? current.filter(value => value !== processType)
      : [...current, processType])
  }

  function toggleMaterialFilter(materialType: MaterialType) {
    setMaterialFilters(current => current.includes(materialType)
      ? current.filter(value => value !== materialType)
      : [...current, materialType])
  }

  function openEditor(candidate: ProcessorCapability) {
    const index = capabilities.findIndex(row => capabilityKey(row) === capabilityKey(candidate))
    const capability = index >= 0 ? { ...capabilities[index] } : { ...candidate }
    setEditorError('')
    setEditor({
      mode: index >= 0 ? 'edit' : 'add',
      index: index >= 0 ? index : null,
      capability,
      dailyCapacityKg: capability.dailyCapacity ? String(toKg(capability.dailyCapacity)) : '',
      pricePerKg: String(capability.pricePerKg ?? ''),
    })
  }

  function updateEditor(patch: Partial<Pick<ConditionEditor, 'dailyCapacityKg' | 'pricePerKg'>>) {
    setEditor(current => (current ? { ...current, ...patch } : current))
    setEditorError('')
  }

  /** 다른 조건에 적어 둔 하루 처리량·단가를 그대로 가져온다. 대개 소재만 다르고 값은 같다. */
  function copySettings(sourceKey: string) {
    if (!editor || !sourceKey) return
    const source = capabilities.find(row => capabilityKey(row) === sourceKey)
    if (!source) return
    setEditor({
      ...editor,
      dailyCapacityKg: String(toKg(source.dailyCapacity)),
      pricePerKg: String(source.pricePerKg),
    })
    setEditorError('')
  }

  async function applyEditor() {
    if (!editor) return
    const message = conditionError(editor.dailyCapacityKg, editor.pricePerKg)
    setEditorError(message)
    if (message) return

    const capability: ProcessorCapability = {
      materialType: editor.capability.materialType,
      processType: editor.capability.processType,
      // 서버는 gram/일 로 받는다. 화면이 kg 으로 묻는 것은 사람이 그렇게 세기 때문이다.
      dailyCapacity: Math.round(Number(editor.dailyCapacityKg) * 1000),
      pricePerKg: Number(editor.pricePerKg),
    }
    const nextCapabilities = editor.mode === 'add'
      ? [...capabilities, capability]
      : capabilities.map((row, index) => index === editor.index ? capability : row)
    setSaveNotice('')
    try {
      if (!data.profile.data) await data.saveProfile.mutateAsync({ region })
      await data.saveConditions.mutateAsync({ capabilities: nextCapabilities })
      setCapabilities(nextCapabilities)
      setEditor(null)
      setSaveNotice('취급 조건이 저장되었어요.')
    } catch { /* mutation error is rendered below */ }
  }

  async function removeEditorCondition() {
    if (!editor || editor.index === null) return
    const nextCapabilities = capabilities.filter((_, index) => index !== editor.index)
    setSaveNotice('')
    try {
      await data.saveConditions.mutateAsync({ capabilities: nextCapabilities })
      setCapabilities(nextCapabilities)
      setEditor(null)
      setSaveNotice('취급 조건이 삭제되었어요.')
    } catch { /* mutation error is rendered below */ }
  }

  async function saveRegion(address: string) {
    setRegion(address)
    setSaveNotice('')
    try {
      await data.saveProfile.mutateAsync({ region: address })
      setSaveNotice('업체 주소가 저장되었어요.')
    } catch { /* mutation error is rendered below */ }
  }

  async function searchAddress() {
    setAddressSearchError('')
    setIsSearchingAddress(true)
    try {
      await openKakaoPostcode(({ address }) => { void saveRegion(address) })
    } catch {
      setAddressSearchError('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSearchingAddress(false)
    }
  }

  if (loading) return <div className="processor-profile-state" role="status">업체 정보를 불러오는 중이에요…</div>
  if (loadError) return <div className="processor-profile-state processor-profile-error" role="alert">{toErrorMessage(data.profile.error ?? data.capabilities.error ?? company.error)}</div>

  const searchResults = materialTypes.flatMap(materialType => processTypes.map(processType => emptyCapability(materialType, processType)))
    .filter(capability => !materialFilters.length || materialFilters.includes(capability.materialType))
    .filter(capability => !processFilters.length || processFilters.includes(capability.processType))

  return <div className="processor-profile-form">
    <section className="processor-profile-card processor-profile-region-card">
      <div className="processor-profile-title"><MapPin size={23} /><div><h2>업체 주소 등록</h2></div></div>
      <div className="processor-profile-address-search"><label className="sr-only" htmlFor="processor-region">업체 주소</label><input id="processor-region" value={region} maxLength={100} readOnly placeholder="주소 검색을 이용해 주세요." /><button type="button" onClick={() => void searchAddress()} disabled={isSearchingAddress || data.saveProfile.isPending}><MagnifyingGlass aria-hidden="true" />{isSearchingAddress || data.saveProfile.isPending ? '저장 중' : '주소 검색'}</button></div>
      {addressSearchError && <p className="processor-profile-address-error" role="alert">{addressSearchError}</p>}
    </section>

    <section className="processor-profile-card processor-profile-image-card">
      <div className="processor-profile-title"><ImageSquare size={23} /><div><h2>업체 대표 이미지</h2></div></div>
      <div className="processor-profile-image-preview">
        {profileImageUrl ? <img src={profileImageUrl} alt="등록된 업체 대표 이미지" /> : <div><ImageSquare size={34} /><span>등록된 이미지가 없어요.</span></div>}
      </div>
      <div className="processor-profile-image-upload">
        <FileUploader accept=".jpg,.jpeg,.png,.webp" maxFiles={1} maxSizeBytes={10 * 1024 * 1024} label="대표 이미지 선택" hint="JPG, PNG, WebP · 최대 10MB · 선택하면 즉시 저장됩니다." showPreview uploading={data.uploadImage.isPending} onChange={files => { if (files[0]) data.uploadImage.mutate(files[0]) }} />
        {data.uploadImage.isSuccess && <p className="processor-profile-image-success" role="status"><CheckCircle weight="fill" />대표 이미지가 저장되었어요.</p>}
        {(data.image.isError || data.uploadImage.isError) && <p className="processor-profile-inline-error" role="alert">{toErrorMessage(data.uploadImage.error ?? data.image.error)}</p>}
      </div>
    </section>

    <section className="processor-profile-card">
      <div className="processor-profile-section-head"><div className="processor-profile-title"><MagnifyingGlass size={23} /><div><h2>취급 조건 검색</h2></div></div><strong className="processor-profile-count">등록된 취급 조건 {capabilities.length}개</strong></div>
      <div className="processor-profile-search-controls">
        {/* 소재도 공정과 같은 방식으로 고른다 — 한 번에 여러 소재를 훑어야 조건을 한 벌로 적는다. */}
        <fieldset><legend>소재</legend><div className="processor-profile-filter-checks">{materialTypes.map(type => <label key={type}><input type="checkbox" checked={materialFilters.includes(type)} onChange={() => toggleMaterialFilter(type)} /><span>{MATERIAL_TYPE_LABEL[type]}</span></label>)}</div></fieldset>
        <fieldset><legend>공정 방식</legend><div className="processor-profile-filter-checks">{processTypes.map(type => <label key={type}><input type="checkbox" checked={processFilters.includes(type)} onChange={() => toggleProcessFilter(type)} /><span>{PROCESS_TYPE_LABEL[type]}</span></label>)}</div></fieldset>
        <strong>검색 결과 {searchResults.length}개</strong>
      </div>
      <div className="processor-profile-search-results">{searchResults.length ? searchResults.map(candidate => {
        const key = capabilityKey(candidate)
        const existing = capabilities.find(row => capabilityKey(row) === key)
        const label = capabilityLabel(candidate)
        return <div className={`processor-profile-search-result${existing ? ' is-registered' : ''}`} key={key}><div><strong>{label}</strong><span>{existing ? `하루 ${toKg(existing.dailyCapacity).toLocaleString()} kg · kg당 ${existing.pricePerKg.toLocaleString()}원` : '하루 처리량과 단가를 등록할 수 있어요.'}</span></div><button type="button" className={existing ? 'is-edit' : ''} aria-label={`${label} ${existing ? '등록됨' : '추가'}`} onClick={() => openEditor(existing ?? candidate)}>{existing ? <><CheckCircle weight="fill" />등록됨</> : <><Plus />추가</>}</button></div>
      }) : <p className="processor-profile-no-results">조건에 맞는 공정이 없어요. 필터를 변경해 주세요.</p>}</div>
    </section>

    <section className="processor-profile-card processor-profile-preview">
      <div className="processor-profile-title"><CheckCircle size={23} /><div><h2>Brand 검색 노출 미리보기</h2></div></div>
      {/*
        등록한 조건을 고치러 검색 칸으로 되돌아가지 않는다 — 이미 눈앞에 있는 줄을 누르면
        그 조건의 편집 모달이 열린다. 검색은 "없는 조건을 새로 만드는" 자리로 남는다.
      */}
      <div className="processor-profile-preview-body"><strong>{region || '업체 주소 미등록'}</strong><div>{capabilities.map(capability => <button type="button" key={capabilityKey(capability)} aria-label={`${capabilityLabel(capability)} 조건 수정`} onClick={() => openEditor(capability)}>{capabilityLabel(capability)} · 하루 {toKg(capability.dailyCapacity).toLocaleString()} kg · kg당 {capability.pricePerKg.toLocaleString()}원</button>)}</div>{capabilities.length > 0 && <small>클릭하면 수정할 수 있어요.</small>}</div>
    </section>

    {(data.saveProfile.isError || data.saveConditions.isError) && <p className="processor-profile-message processor-profile-error" role="alert">{toErrorMessage(data.saveProfile.error ?? data.saveConditions.error)}</p>}
    {saveNotice && <p className="processor-profile-message processor-profile-success" role="status"><CheckCircle weight="fill" />{saveNotice}</p>}

    {editor && (() => {
      const label = capabilityLabel(editor.capability)
      const capacity = Number(editor.dailyCapacityKg)
      const price = Number(editor.pricePerKg)
      return <div className="processor-profile-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setEditor(null) }}>
        <section className="processor-profile-modal" role="dialog" aria-modal="true" aria-labelledby="processor-condition-dialog-title">
          <header><div><span>{editor.mode === 'edit' ? '등록된 조건 수정' : '새 취급 조건'}</span><h2 id="processor-condition-dialog-title" ref={editorTitleRef} tabIndex={-1}>{label} 조건 {editor.mode === 'edit' ? '수정' : '추가'}</h2></div><button type="button" aria-label="모달 닫기" onClick={() => setEditor(null)}><X /></button></header>
          <div className="processor-profile-modal-content">
            <div className="processor-profile-modal-summary"><strong>{MATERIAL_TYPE_LABEL[editor.capability.materialType]}</strong><span>{PROCESS_TYPE_LABEL[editor.capability.processType]}</span></div>

            {capabilities.some(row => capabilityKey(row) !== capabilityKey(editor.capability)) && <div className="processor-profile-copy-row"><Copy /><label><span>다른 조건 설정 가져오기</span><select aria-label={`${label} 설정 가져오기`} defaultValue="" onChange={event => copySettings(event.target.value)}><option value="">선택하세요</option>{capabilities.filter(row => capabilityKey(row) !== capabilityKey(editor.capability)).map(row => <option key={capabilityKey(row)} value={capabilityKey(row)}>{capabilityLabel(row)}</option>)}</select></label></div>}

            {/*
              적는 것은 둘뿐이다. 이 둘로 브랜드 화면의 예상 금액과 소요일이 나오므로,
              적으면서 그 결과를 바로 아래에 보여준다 — 숫자만 넣고 "이게 얼마로 보이지" 를
              따로 계산하게 두지 않는다.
            */}
            <div className="processor-profile-condition-fields">
              <label><span>하루 처리량</span><div className="processor-profile-unit"><input aria-label="하루 처리량" type="text" inputMode="decimal" value={editor.dailyCapacityKg} placeholder="예: 200" onChange={event => { if (/^\d*\.?\d*$/.test(event.target.value)) updateEditor({ dailyCapacityKg: event.target.value }) }} /><b>kg / 일</b></div></label>
              <label><span>kg 당 단가</span><div className="processor-profile-unit"><input aria-label="kg 당 단가" type="text" inputMode="numeric" value={editor.pricePerKg} placeholder="예: 3000" onChange={event => { if (/^\d*$/.test(event.target.value)) updateEditor({ pricePerKg: event.target.value }) }} /><b>원</b></div></label>
            </div>

            {capacity > 0 && price > 0 && <p className="processor-profile-estimate">브랜드가 <strong>{capacity.toLocaleString()} kg</strong> 을 맡기면 <strong>{(capacity * price).toLocaleString()}원 · 1일</strong> 로 보입니다. 그보다 많으면 날짜가 그만큼 늘어납니다.</p>}

            <p className="processor-profile-help">
              소요일은 따로 적지 않습니다 — 요청 수량을 하루 처리량으로 나눠 서버가 셉니다.
            </p>

            {editorError && <p className="processor-profile-inline-error" role="alert">{editorError}</p>}
          </div>
          <footer>{editor.mode === 'edit' && <button type="button" className="processor-profile-modal-delete" disabled={data.saveConditions.isPending} onClick={() => void removeEditorCondition()}><Trash />조건 삭제</button>}<div><button type="button" className="processor-profile-modal-cancel" disabled={data.saveConditions.isPending} onClick={() => setEditor(null)}>취소</button><button type="button" className="processor-profile-modal-apply" disabled={data.saveConditions.isPending} onClick={() => void applyEditor()}>{data.saveConditions.isPending ? '저장 중…' : editor.mode === 'edit' ? '변경사항 적용' : '조건 추가'}</button></div></footer>
        </section>
      </div>
    })()}
  </div>
}
