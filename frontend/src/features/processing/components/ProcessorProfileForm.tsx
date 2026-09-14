import { CheckCircle, Copy, ImageSquare, MagnifyingGlass, MapPin, Plus, Trash, X } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { FileUploader } from '../../../components/common/FileUploader.tsx'
import { useCompanyProfile } from '../../company/hooks/useCompanyProfile.ts'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import { toErrorMessage } from '../../../utils/errors.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import { useProcessorProfile } from '../hooks/useProcessorProfile.ts'
import { MATERIAL_TYPE_LABEL, type MaterialType, type ProcessorCapability, type ProcessorLeadTime } from '../types/processorProfile.types.ts'
import './processor-profile.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
const processTypes = Object.keys(PROCESS_TYPE_LABEL) as ProcessType[]
const emptyCapability = (materialType: MaterialType = 'DENIM', processType: ProcessType = 'CLEANING'): ProcessorCapability => ({ materialType, processType, minQuantity: 1, maxQuantity: 1 })
const emptyLeadTime = (capability: ProcessorCapability): ProcessorLeadTime => ({ materialType: capability.materialType, processType: capability.processType, maxQuantity: capability.maxQuantity, leadDays: 1 })
const capabilityKey = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${materialType}:${processType}`
const capabilityLabel = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${MATERIAL_TYPE_LABEL[materialType]} · ${PROCESS_TYPE_LABEL[processType]}`
const readQuantity = (value: string, current: number) => /^\d*$/.test(value) ? Number(value) : current

interface ConditionEditor {
  mode: 'add' | 'edit'
  index: number | null
  capability: ProcessorCapability
  ranges: ProcessorLeadTime[]
}

function conditionError(capability: ProcessorCapability, ranges: ProcessorLeadTime[]) {
  if (!Number.isInteger(capability.minQuantity) || !Number.isInteger(capability.maxQuantity) || capability.minQuantity < 1) return '수량은 1 g 이상의 정수로 입력해 주세요.'
  if (capability.maxQuantity < capability.minQuantity) return '최대 수량은 최소 수량보다 크거나 같아야 해요.'
  const sorted = [...ranges].sort((a, b) => a.maxQuantity - b.maxQuantity)
  if (!sorted.length || sorted.at(-1)!.maxQuantity !== capability.maxQuantity) return '마지막 구간은 최대 처리 수량까지 포함해야 해요.'
  if (sorted.some((row, index) => !Number.isInteger(row.leadDays) || row.leadDays < 1 || row.maxQuantity < capability.minQuantity || (index > 0 && row.maxQuantity <= sorted[index - 1].maxQuantity))) return '소요일과 수량 구간을 순서대로 확인해 주세요.'
  return ''
}

export function ProcessorProfileForm() {
  const data = useProcessorProfile()
  const company = useCompanyProfile()
  const initialized = useRef(false)
  const editorTitleRef = useRef<HTMLHeadingElement>(null)
  const [region, setRegion] = useState('')
  const [capabilities, setCapabilities] = useState<ProcessorCapability[]>([])
  const [leadTimes, setLeadTimes] = useState<ProcessorLeadTime[]>([])
  const [materialFilter, setMaterialFilter] = useState<MaterialType | 'ALL'>('ALL')
  const [processFilters, setProcessFilters] = useState<ProcessType[]>([])
  const [registrationFilter, setRegistrationFilter] = useState<'ALL' | 'REGISTERED' | 'UNREGISTERED'>('ALL')
  const [editor, setEditor] = useState<ConditionEditor | null>(null)
  const [rangeDraft, setRangeDraft] = useState('')
  const [editorError, setEditorError] = useState('')
  const [saveNotice, setSaveNotice] = useState('')
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const loading = data.profile.isPending || data.capabilities.isPending || data.leadTimes.isPending || company.isPending
  const loadError = data.profile.isError || data.capabilities.isError || data.leadTimes.isError || company.isError
  const editorOpen = editor !== null
  const profileImageUrl = useObjectUrl(data.image.data)

  useEffect(() => {
    if (initialized.current || loading || loadError) return
    initialized.current = true
    const loadedCapabilities = data.capabilities.data ?? []
    setRegion(data.profile.data?.region ?? company.profile?.address ?? '')
    setCapabilities(loadedCapabilities)
    setLeadTimes(data.leadTimes.data?.length ? data.leadTimes.data : loadedCapabilities.map(emptyLeadTime))
  }, [company.profile?.address, data.capabilities.data, data.leadTimes.data, data.profile.data, loadError, loading])

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

  function openEditor(candidate: ProcessorCapability) {
    const index = capabilities.findIndex(row => capabilityKey(row) === capabilityKey(candidate))
    const capability = index >= 0 ? { ...capabilities[index] } : { ...candidate }
    const ranges = index >= 0
      ? leadTimes.filter(row => capabilityKey(row) === capabilityKey(capability)).sort((a, b) => a.maxQuantity - b.maxQuantity).map(row => ({ ...row }))
      : [emptyLeadTime(capability)]
    setRangeDraft('')
    setEditorError('')
    setEditor({ mode: index >= 0 ? 'edit' : 'add', index: index >= 0 ? index : null, capability, ranges })
  }

  function updateEditorCapability(patch: Partial<ProcessorCapability>) {
    setEditor(current => {
      if (!current) return current
      const capability = { ...current.capability, ...patch }
      const sorted = [...current.ranges].sort((a, b) => a.maxQuantity - b.maxQuantity)
      const withinRange = sorted.filter(row => row.maxQuantity >= capability.minQuantity && row.maxQuantity < capability.maxQuantity)
      const finalRow = sorted.at(-1) ?? emptyLeadTime(capability)
      return {
        ...current,
        capability,
        ranges: [...withinRange, { ...finalRow, materialType: capability.materialType, processType: capability.processType, maxQuantity: capability.maxQuantity }],
      }
    })
    setEditorError('')
  }

  function updateEditorRange(rangeIndex: number, patch: Partial<ProcessorLeadTime>) {
    setEditor(current => current ? { ...current, ranges: current.ranges.map((row, index) => index === rangeIndex ? { ...row, ...patch } : row) } : current)
    setEditorError('')
  }

  function addEditorRange() {
    if (!editor) return
    const boundary = Number(rangeDraft)
    const ranges = [...editor.ranges].sort((a, b) => a.maxQuantity - b.maxQuantity)
    const finalRow = ranges.at(-1)
    const lastStart = ranges.length === 1 ? editor.capability.minQuantity : ranges.at(-2)!.maxQuantity + 1
    if (!Number.isInteger(boundary) || !finalRow || boundary < lastStart || boundary >= finalRow.maxQuantity) {
      setEditorError(`최대 수량은 ${lastStart.toLocaleString()}~${Math.max(lastStart, (finalRow?.maxQuantity ?? 1) - 1).toLocaleString()} g 사이로 입력해 주세요.`)
      return
    }
    setEditor({ ...editor, ranges: [...ranges.slice(0, -1), { ...finalRow, maxQuantity: boundary }, finalRow] })
    setRangeDraft('')
    setEditorError('')
  }

  function removeEditorRange(rangeIndex: number) {
    if (!editor || editor.ranges.length <= 1) return
    const ranges = [...editor.ranges].sort((a, b) => a.maxQuantity - b.maxQuantity)
    const removed = ranges[rangeIndex]
    const remaining = ranges.filter((_, index) => index !== rangeIndex)
    if (rangeIndex === ranges.length - 1) remaining[remaining.length - 1] = { ...remaining.at(-1)!, maxQuantity: removed.maxQuantity }
    setEditor({ ...editor, ranges: remaining })
    setEditorError('')
  }

  function copySettings(sourceKey: string) {
    if (!editor || !sourceKey) return
    const source = capabilities.find(row => capabilityKey(row) === sourceKey)
    if (!source) return
    const ranges = leadTimes.filter(row => capabilityKey(row) === sourceKey).sort((a, b) => a.maxQuantity - b.maxQuantity)
      .map(row => ({ ...row, materialType: editor.capability.materialType, processType: editor.capability.processType }))
    setEditor({ ...editor, capability: { ...editor.capability, minQuantity: source.minQuantity, maxQuantity: source.maxQuantity }, ranges })
    setEditorError('')
  }

  async function applyEditor() {
    if (!editor) return
    const message = conditionError(editor.capability, editor.ranges)
    setEditorError(message)
    if (message) return
    const key = capabilityKey(editor.capability)
    const nextCapabilities = editor.mode === 'add'
      ? [...capabilities, editor.capability]
      : capabilities.map((row, index) => index === editor.index ? editor.capability : row)
    const nextLeadTimes = [...leadTimes.filter(row => capabilityKey(row) !== key), ...editor.ranges]
    setSaveNotice('')
    try {
      if (!data.profile.data) await data.saveProfile.mutateAsync({ region })
      await data.saveConditions.mutateAsync({
        capabilities: { capabilities: nextCapabilities },
        leadTimes: { leadTimes: [...nextLeadTimes].sort((a, b) => a.materialType.localeCompare(b.materialType) || a.processType.localeCompare(b.processType) || a.maxQuantity - b.maxQuantity) },
      })
      setCapabilities(nextCapabilities)
      setLeadTimes(nextLeadTimes)
      setEditor(null)
      setSaveNotice('취급 조건이 저장되었어요.')
    } catch { /* mutation error is rendered below */ }
  }

  async function removeEditorCondition() {
    if (!editor || editor.index === null) return
    const key = capabilityKey(editor.capability)
    const nextCapabilities = capabilities.filter((_, index) => index !== editor.index)
    const nextLeadTimes = leadTimes.filter(row => capabilityKey(row) !== key)
    setSaveNotice('')
    try {
      await data.saveConditions.mutateAsync({ capabilities: { capabilities: nextCapabilities }, leadTimes: { leadTimes: nextLeadTimes } })
      setCapabilities(nextCapabilities)
      setLeadTimes(nextLeadTimes)
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
  if (loadError) return <div className="processor-profile-state processor-profile-error" role="alert">{toErrorMessage(data.profile.error ?? data.capabilities.error ?? data.leadTimes.error ?? company.error)}</div>

  const searchResults = materialTypes.flatMap(materialType => processTypes.map(processType => emptyCapability(materialType, processType)))
    .filter(capability => materialFilter === 'ALL' || capability.materialType === materialFilter)
    .filter(capability => !processFilters.length || processFilters.includes(capability.processType))
    .filter(capability => {
      if (registrationFilter === 'ALL') return true
      const registered = capabilities.some(row => capabilityKey(row) === capabilityKey(capability))
      return registrationFilter === 'REGISTERED' ? registered : !registered
    })

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
        <label><span>소재</span><select aria-label="소재 필터" value={materialFilter} onChange={event => setMaterialFilter(event.target.value as MaterialType | 'ALL')}><option value="ALL">전체 소재</option>{materialTypes.map(type => <option key={type} value={type}>{MATERIAL_TYPE_LABEL[type]}</option>)}</select></label>
        <label><span>등록 상태</span><select aria-label="등록 상태 필터" value={registrationFilter} onChange={event => setRegistrationFilter(event.target.value as typeof registrationFilter)}><option value="ALL">전체</option><option value="REGISTERED">등록됨</option><option value="UNREGISTERED">미등록</option></select></label>
        <fieldset><legend>공정 방식</legend><div className="processor-profile-filter-checks">{processTypes.map(type => <label key={type}><input type="checkbox" checked={processFilters.includes(type)} onChange={() => toggleProcessFilter(type)} /><span>{PROCESS_TYPE_LABEL[type]}</span></label>)}</div></fieldset>
        <strong>검색 결과 {searchResults.length}개</strong>
      </div>
      <div className="processor-profile-search-results">{searchResults.length ? searchResults.map(candidate => {
        const key = capabilityKey(candidate)
        const existing = capabilities.find(row => capabilityKey(row) === key)
        const label = capabilityLabel(candidate)
        const ranges = existing ? leadTimes.filter(row => capabilityKey(row) === key) : []
        return <div className={`processor-profile-search-result${existing ? ' is-registered' : ''}`} key={key}><div><strong>{label}</strong><span>{existing ? `${existing.minQuantity.toLocaleString()}~${existing.maxQuantity.toLocaleString()} g · 소요일 ${ranges.length}개 구간` : '수량과 소요일을 등록할 수 있어요.'}</span></div><button type="button" className={existing ? 'is-edit' : ''} aria-label={`${label} ${existing ? '등록됨 · 수정' : '추가'}`} onClick={() => openEditor(existing ?? candidate)}>{existing ? <><CheckCircle weight="fill" />등록됨 · 수정</> : <><Plus />추가</>}</button></div>
      }) : <p className="processor-profile-no-results">조건에 맞는 공정이 없어요. 필터를 변경해 주세요.</p>}</div>
    </section>

    <section className="processor-profile-card processor-profile-preview">
      <div className="processor-profile-title"><CheckCircle size={23} /><div><h2>Brand 검색 노출 미리보기</h2></div></div>
      <div className="processor-profile-preview-body"><strong>{region || '업체 주소 미등록'}</strong><div>{capabilities.map(capability => <span key={capabilityKey(capability)}>{capabilityLabel(capability)} · {capability.minQuantity.toLocaleString()}~{capability.maxQuantity.toLocaleString()} g</span>)}</div></div>
    </section>

    {(data.saveProfile.isError || data.saveConditions.isError) && <p className="processor-profile-message processor-profile-error" role="alert">{toErrorMessage(data.saveProfile.error ?? data.saveConditions.error)}</p>}
    {saveNotice && <p className="processor-profile-message processor-profile-success" role="status"><CheckCircle weight="fill" />{saveNotice}</p>}

    {editor && (() => {
      const label = capabilityLabel(editor.capability)
      const ranges = [...editor.ranges].sort((a, b) => a.maxQuantity - b.maxQuantity)
      return <div className="processor-profile-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setEditor(null) }}>
        <section className="processor-profile-modal" role="dialog" aria-modal="true" aria-labelledby="processor-condition-dialog-title">
          <header><div><span>{editor.mode === 'edit' ? '등록된 조건 수정' : '새 취급 조건'}</span><h2 id="processor-condition-dialog-title" ref={editorTitleRef} tabIndex={-1}>{label} 조건 {editor.mode === 'edit' ? '수정' : '추가'}</h2></div><button type="button" aria-label="모달 닫기" onClick={() => setEditor(null)}><X /></button></header>
          <div className="processor-profile-modal-content">
            <div className="processor-profile-modal-summary"><strong>{MATERIAL_TYPE_LABEL[editor.capability.materialType]}</strong><span>{PROCESS_TYPE_LABEL[editor.capability.processType]}</span></div>
            {capabilities.some(row => capabilityKey(row) !== capabilityKey(editor.capability)) && <div className="processor-profile-copy-row"><Copy /><label><span>다른 조건 설정 가져오기</span><select aria-label={`${label} 설정 가져오기`} defaultValue="" onChange={event => copySettings(event.target.value)}><option value="">선택하세요</option>{capabilities.filter(row => capabilityKey(row) !== capabilityKey(editor.capability)).map(row => <option key={capabilityKey(row)} value={capabilityKey(row)}>{capabilityLabel(row)}</option>)}</select></label></div>}
            <div className="processor-profile-condition-fields">
              <label><span>최소 처리 수량</span><div className="processor-profile-unit"><input aria-label="최소 처리 수량" type="text" inputMode="numeric" pattern="[0-9]*" value={editor.capability.minQuantity} onChange={event => updateEditorCapability({ minQuantity: readQuantity(event.target.value, editor.capability.minQuantity) })} /><b>g</b></div></label>
              <label><span>최대 처리 수량</span><div className="processor-profile-unit"><input aria-label="최대 처리 수량" type="text" inputMode="numeric" pattern="[0-9]*" value={editor.capability.maxQuantity} onChange={event => updateEditorCapability({ maxQuantity: readQuantity(event.target.value, editor.capability.maxQuantity) })} /><b>g</b></div></label>
            </div>
            <div className="processor-profile-ranges">
              <div className="processor-profile-ranges-head"><div><strong>수량 구간별 평균 소요일</strong><p>마지막 구간은 최대 처리 수량에 자동으로 맞춰져요.</p></div></div>
              <div className="processor-profile-range-adder"><label><span>새 구간 최대 수량</span><div className="processor-profile-unit"><input aria-label={`${label} 새 구간 최대 수량`} type="text" inputMode="numeric" pattern="[0-9]*" value={rangeDraft} placeholder="예: 1000" onChange={event => { if (/^\d*$/.test(event.target.value)) setRangeDraft(event.target.value) }} /><b>g</b></div></label><button type="button" aria-label={`${label} 소요일 구간 추가`} onClick={addEditorRange} disabled={editor.capability.maxQuantity <= editor.capability.minQuantity}><Plus />구간 추가</button></div>
              <div className="processor-profile-range-list">{ranges.map((range, rangeIndex) => {
                const rangeStart = rangeIndex === 0 ? editor.capability.minQuantity : ranges[rangeIndex - 1].maxQuantity + 1
                return <div className="processor-profile-range" key={`${range.maxQuantity}:${rangeIndex}`}><span className="processor-profile-range-number">{rangeIndex + 1}</span><div className="processor-profile-range-start"><span>시작</span><strong>{rangeStart.toLocaleString()} g</strong></div><span className="processor-profile-range-separator">~</span><label><span>최대</span><div className="processor-profile-unit"><input aria-label={`${label} 구간 최대 수량 ${rangeIndex + 1}`} type="text" inputMode="numeric" pattern="[0-9]*" value={range.maxQuantity} readOnly={rangeIndex === ranges.length - 1} onChange={event => updateEditorRange(rangeIndex, { maxQuantity: readQuantity(event.target.value, range.maxQuantity) })} /><b>g</b></div></label><label><span>평균 소요일</span><div className="processor-profile-unit"><input aria-label={`${label} 평균 소요일 ${rangeIndex + 1}`} type="text" inputMode="numeric" pattern="[0-9]*" value={range.leadDays} onChange={event => updateEditorRange(rangeIndex, { leadDays: readQuantity(event.target.value, range.leadDays) })} /><b>일</b></div></label><button type="button" aria-label={`${label} 소요일 구간 ${rangeIndex + 1} 삭제`} disabled={ranges.length === 1} onClick={() => removeEditorRange(rangeIndex)}><Trash /></button></div>
              })}</div>
            </div>
            {editorError && <p className="processor-profile-inline-error" role="alert">{editorError}</p>}
          </div>
          <footer>{editor.mode === 'edit' && <button type="button" className="processor-profile-modal-delete" disabled={data.saveConditions.isPending} onClick={() => void removeEditorCondition()}><Trash />조건 삭제</button>}<div><button type="button" className="processor-profile-modal-cancel" disabled={data.saveConditions.isPending} onClick={() => setEditor(null)}>취소</button><button type="button" className="processor-profile-modal-apply" disabled={data.saveConditions.isPending} onClick={() => void applyEditor()}>{data.saveConditions.isPending ? '저장 중…' : editor.mode === 'edit' ? '변경사항 적용' : '조건 추가'}</button></div></footer>
        </section>
      </div>
    })()}
  </div>
}
