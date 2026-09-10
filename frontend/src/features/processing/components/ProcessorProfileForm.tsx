import { CheckCircle, Clock, Factory, MagnifyingGlass, MapPin, Plus, Trash } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { toErrorMessage } from '../../../utils/errors.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import { MATERIAL_TYPE_LABEL, type MaterialType, type ProcessorCapability, type ProcessorLeadTime } from '../types/processorProfile.types.ts'
import { useProcessorProfile } from '../hooks/useProcessorProfile.ts'
import './processor-profile.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
const processTypes = Object.keys(PROCESS_TYPE_LABEL) as ProcessType[]
const emptyCapability = (materialType: MaterialType = 'DENIM', processType: ProcessType = 'CLEANING'): ProcessorCapability => ({ materialType, processType, minQuantity: 1, maxQuantity: 1 })
const emptyLeadTime = (capability: ProcessorCapability = emptyCapability()): ProcessorLeadTime => ({ materialType: capability.materialType, processType: capability.processType, maxQuantity: capability.maxQuantity, leadDays: 1 })
const capabilityKey = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${materialType}:${processType}`
const capabilityLabel = ({ materialType, processType }: Pick<ProcessorCapability, 'materialType' | 'processType'>) => `${MATERIAL_TYPE_LABEL[materialType]} · ${PROCESS_TYPE_LABEL[processType]}`
const readQuantity = (value: string, current: number) => /^\d*$/.test(value) ? Number(value) : current

function nextAvailableCapability(capabilities: ProcessorCapability[]) {
  const used = new Set(capabilities.map(capabilityKey))
  for (const materialType of materialTypes) {
    for (const processType of processTypes) {
      const candidate = emptyCapability(materialType, processType)
      if (!used.has(capabilityKey(candidate))) return candidate
    }
  }
  return null
}

export function ProcessorProfileForm() {
  const data = useProcessorProfile()
  const initialized = useRef(false)
  const [region, setRegion] = useState('')
  const [capabilities, setCapabilities] = useState<ProcessorCapability[]>([emptyCapability()])
  const [leadTimes, setLeadTimes] = useState<ProcessorLeadTime[]>([emptyLeadTime()])
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState(false)
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const loading = data.profile.isPending || data.capabilities.isPending || data.leadTimes.isPending
  const loadError = data.profile.isError || data.capabilities.isError || data.leadTimes.isError

  useEffect(() => {
    if (initialized.current || loading || loadError) return
    initialized.current = true
    const loadedCapabilities = data.capabilities.data?.length ? data.capabilities.data : [emptyCapability()]
    setRegion(data.profile.data?.region ?? '')
    setCapabilities(loadedCapabilities)
    setLeadTimes(data.leadTimes.data?.length ? data.leadTimes.data : loadedCapabilities.map(emptyLeadTime))
  }, [data.capabilities.data, data.leadTimes.data, data.profile.data, loadError, loading])

  function updateCapability(index: number, patch: Partial<ProcessorCapability>) {
    const previous = capabilities[index]
    const next = { ...previous, ...patch }
    const previousKey = capabilityKey(previous)
    setCapabilities(rows => rows.map((row, rowIndex) => rowIndex === index ? next : row))
    setLeadTimes(rows => {
      const unrelated = rows.filter(row => capabilityKey(row) !== previousKey)
      const related = rows.filter(row => capabilityKey(row) === previousKey)
        .map(row => ({ ...row, materialType: next.materialType, processType: next.processType }))
        .sort((a, b) => a.maxQuantity - b.maxQuantity)
      const withinRange = related.filter(row => row.maxQuantity >= next.minQuantity && row.maxQuantity < next.maxQuantity)
      const finalRow = related.at(-1) ?? emptyLeadTime(next)
      return [...unrelated, ...withinRange, { ...finalRow, materialType: next.materialType, processType: next.processType, maxQuantity: next.maxQuantity }]
    })
    setValidation('')
    setSaved(false)
  }

  function addCapability() {
    const capability = nextAvailableCapability(capabilities)
    if (!capability) return
    setCapabilities(rows => [...rows, capability])
    setLeadTimes(rows => [...rows, emptyLeadTime(capability)])
    setValidation('')
    setSaved(false)
  }

  function removeCapability(index: number) {
    const target = capabilities[index]
    setCapabilities(rows => rows.filter((_, rowIndex) => rowIndex !== index))
    setLeadTimes(rows => rows.filter(row => capabilityKey(row) !== capabilityKey(target)))
    setValidation('')
    setSaved(false)
  }

  function addLeadTimeRange(capability: ProcessorCapability) {
    const key = capabilityKey(capability)
    setLeadTimes(rows => {
      const related = rows.filter(row => capabilityKey(row) === key).sort((a, b) => a.maxQuantity - b.maxQuantity)
      if (!related.length) return [...rows, emptyLeadTime(capability)]
      const finalRow = related.at(-1)!
      const rangeStart = related.length === 1 ? capability.minQuantity : related.at(-2)!.maxQuantity + 1
      if (rangeStart >= finalRow.maxQuantity) return rows
      const splitAt = Math.floor((rangeStart + finalRow.maxQuantity) / 2)
      return [...rows.filter(row => capabilityKey(row) !== key), ...related.slice(0, -1), { ...finalRow, maxQuantity: splitAt }, finalRow]
    })
    setValidation('')
    setSaved(false)
  }

  function updateLeadTime(capability: ProcessorCapability, previousMax: number, patch: Partial<ProcessorLeadTime>) {
    const key = capabilityKey(capability)
    setLeadTimes(rows => rows.map(row => capabilityKey(row) === key && row.maxQuantity === previousMax ? { ...row, ...patch } : row))
    setValidation('')
    setSaved(false)
  }

  function removeLeadTimeRange(capability: ProcessorCapability, maxQuantity: number) {
    const key = capabilityKey(capability)
    setLeadTimes(rows => {
      const related = rows.filter(row => capabilityKey(row) === key).sort((a, b) => a.maxQuantity - b.maxQuantity)
      if (related.length <= 1) return rows
      const targetIndex = related.findIndex(row => row.maxQuantity === maxQuantity)
      const remaining = related.filter((_, index) => index !== targetIndex)
      if (targetIndex === related.length - 1) remaining[remaining.length - 1] = { ...remaining.at(-1)!, maxQuantity }
      return [...rows.filter(row => capabilityKey(row) !== key), ...remaining]
    })
    setValidation('')
    setSaved(false)
  }

  async function searchAddress() {
    setAddressSearchError('')
    setIsSearchingAddress(true)
    try {
      await openKakaoPostcode(({ address }) => {
        setRegion(address)
        setValidation('')
        setSaved(false)
      })
    } catch {
      setAddressSearchError('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSearchingAddress(false)
    }
  }

  function validate() {
    if (!region.trim()) return '지역을 입력해 주세요.'
    if (capabilities.some(row => !Number.isInteger(row.minQuantity) || !Number.isInteger(row.maxQuantity) || row.minQuantity < 1 || row.maxQuantity < row.minQuantity)) return '소재·공정별 수량은 1 g 이상의 정수이며 최소 수량이 최대 수량보다 클 수 없어요.'
    if (new Set(capabilities.map(capabilityKey)).size !== capabilities.length) return '동일한 소재·공정 조합은 한 번만 등록할 수 있어요.'
    if (leadTimes.some(row => !Number.isInteger(row.maxQuantity) || !Number.isInteger(row.leadDays) || row.maxQuantity < 1 || row.leadDays < 1)) return '소요일 구간과 일수는 양의 정수로 입력해 주세요.'
    const capabilityKeys = new Set(capabilities.map(capabilityKey))
    if (leadTimes.some(row => !capabilityKeys.has(capabilityKey(row)))) return '취급 조건과 연결되지 않은 소요일 구간이 있어요.'
    for (const capability of capabilities) {
      const ranges = leadTimes.filter(row => capabilityKey(row) === capabilityKey(capability)).sort((a, b) => a.maxQuantity - b.maxQuantity)
      if (!ranges.length || ranges.at(-1)!.maxQuantity !== capability.maxQuantity) return `${capabilityLabel(capability)}의 마지막 소요일 구간은 최대 처리 수량과 같아야 해요.`
      if (ranges.some((row, index) => row.maxQuantity < capability.minQuantity || row.maxQuantity > capability.maxQuantity || (index > 0 && row.maxQuantity <= ranges[index - 1].maxQuantity))) return `${capabilityLabel(capability)}의 소요일 구간을 순서대로 확인해 주세요.`
    }
    return ''
  }

  async function submit() {
    const message = validate()
    setValidation(message)
    setSaved(false)
    if (message) return
    try {
      await data.save.mutateAsync({
        profile: { region: region.trim() },
        capabilities: { capabilities },
        leadTimes: { leadTimes: [...leadTimes].sort((a, b) => a.materialType.localeCompare(b.materialType) || a.processType.localeCompare(b.processType) || a.maxQuantity - b.maxQuantity) },
      })
      setSaved(true)
    } catch { /* mutation error is rendered below */ }
  }

  if (loading) return <div className="processor-profile-state" role="status">업체 정보를 불러오는 중이에요…</div>
  if (loadError) return <div className="processor-profile-state processor-profile-error" role="alert">{toErrorMessage(data.profile.error ?? data.capabilities.error ?? data.leadTimes.error)}</div>

  return <div className="processor-profile-form">
    <header className="processor-profile-heading"><div><span>PROCESSOR SETTINGS</span><h1>가공업체 정보 등록</h1><p>처리 가능한 소재와 공정, 소재·공정별 예상 소요일을 등록하면 Brand 검색에 노출돼요.</p></div><Factory size={36} weight="duotone" /></header>
    <section className="processor-profile-card">
      <div className="processor-profile-title"><MapPin size={23} /><div><h2>업체 지역</h2><p>Brand가 가공업체를 비교할 때 표시되는 지역이에요.</p></div></div>
      <div className="processor-profile-field"><label htmlFor="processor-region">지역</label><div className="processor-profile-address-search"><input id="processor-region" value={region} maxLength={100} readOnly placeholder="주소 검색을 이용해 주세요." /><button type="button" onClick={() => void searchAddress()} disabled={isSearchingAddress}><MagnifyingGlass aria-hidden="true" />{isSearchingAddress ? '불러오는 중' : '주소 검색'}</button></div></div>
      {addressSearchError && <p className="processor-profile-address-error" role="alert">{addressSearchError}</p>}
    </section>
    <section className="processor-profile-card">
      <div className="processor-profile-section-head"><div className="processor-profile-title"><Factory size={23} /><div><h2>취급 소재·공정 및 소요일</h2><p>취급 조건을 고른 뒤 같은 카드에서 수량 구간별 평균 소요일을 설정하세요.</p></div></div><button type="button" className="processor-profile-secondary" onClick={addCapability} disabled={capabilities.length >= materialTypes.length * processTypes.length}><Plus />조건 추가</button></div>
      <div className="processor-profile-capability-list">{capabilities.map((capability, capabilityIndex) => {
        const label = capabilityLabel(capability)
        const ranges = leadTimes.filter(row => capabilityKey(row) === capabilityKey(capability)).sort((a, b) => a.maxQuantity - b.maxQuantity)
        return <article className="processor-profile-capability" key={`${capabilityKey(capability)}:${capabilityIndex}`}>
          <div className="processor-profile-capability-head"><div><span>취급 조건 {capabilityIndex + 1}</span><h3>{label}</h3></div><button type="button" aria-label={`가공 조건 ${capabilityIndex + 1} 삭제`} disabled={capabilities.length === 1} onClick={() => removeCapability(capabilityIndex)}><Trash /></button></div>
          <div className="processor-profile-condition-fields">
            <label><span>소재</span><select aria-label={`소재 ${capabilityIndex + 1}`} value={capability.materialType} onChange={event => updateCapability(capabilityIndex, { materialType: event.target.value as MaterialType })}>{materialTypes.map(type => <option key={type} value={type}>{MATERIAL_TYPE_LABEL[type]}</option>)}</select></label>
            <label><span>공정</span><select aria-label={`공정 ${capabilityIndex + 1}`} value={capability.processType} onChange={event => updateCapability(capabilityIndex, { processType: event.target.value as ProcessType })}>{processTypes.map(type => <option key={type} value={type}>{PROCESS_TYPE_LABEL[type]}</option>)}</select></label>
            <label><span>최소 처리 수량</span><div className="processor-profile-unit"><input aria-label={`최소 수량 ${capabilityIndex + 1}`} type="text" inputMode="numeric" pattern="[0-9]*" value={capability.minQuantity} onChange={event => updateCapability(capabilityIndex, { minQuantity: readQuantity(event.target.value, capability.minQuantity) })} /><b>g</b></div></label>
            <label><span>최대 처리 수량</span><div className="processor-profile-unit"><input aria-label={`최대 수량 ${capabilityIndex + 1}`} type="text" inputMode="numeric" pattern="[0-9]*" value={capability.maxQuantity} onChange={event => updateCapability(capabilityIndex, { maxQuantity: readQuantity(event.target.value, capability.maxQuantity) })} /><b>g</b></div></label>
          </div>
          <div className="processor-profile-ranges">
            <div className="processor-profile-ranges-head"><div><Clock size={18} /><div><strong>수량 구간별 평균 소요일</strong><p>요청 수량이 포함되는 구간의 기간이 Brand에게 표시돼요.</p></div></div><button type="button" onClick={() => addLeadTimeRange(capability)} disabled={!ranges.length || (ranges.length === 1 ? capability.minQuantity : ranges.at(-2)!.maxQuantity + 1) >= ranges.at(-1)!.maxQuantity} aria-label={`${label} 소요일 구간 추가`}><Plus />구간 나누기</button></div>
            <div className="processor-profile-range-list">{ranges.map((range, rangeIndex) => {
              const rangeStart = rangeIndex === 0 ? capability.minQuantity : ranges[rangeIndex - 1].maxQuantity + 1
              return <div className="processor-profile-range" key={`${range.maxQuantity}:${rangeIndex}`}>
                <span className="processor-profile-range-number">{rangeIndex + 1}</span>
                <div className="processor-profile-range-start"><span>시작 수량</span><strong>{rangeStart.toLocaleString()} g</strong></div>
                <span className="processor-profile-range-separator">~</span>
                <label><span>최대 수량</span><div className="processor-profile-unit"><input aria-label={`${label} 구간 최대 수량 ${rangeIndex + 1}`} type="text" inputMode="numeric" pattern="[0-9]*" value={range.maxQuantity} readOnly={rangeIndex === ranges.length - 1} onChange={event => updateLeadTime(capability, range.maxQuantity, { maxQuantity: readQuantity(event.target.value, range.maxQuantity) })} /><b>g</b></div></label>
                <label><span>평균 소요일</span><div className="processor-profile-unit"><input aria-label={`${label} 평균 소요일 ${rangeIndex + 1}`} type="number" min="1" step="1" value={range.leadDays} onChange={event => updateLeadTime(capability, range.maxQuantity, { leadDays: Number(event.target.value) })} /><b>일</b></div></label>
                <button type="button" aria-label={`${label} 소요일 구간 ${rangeIndex + 1} 삭제`} disabled={ranges.length === 1} onClick={() => removeLeadTimeRange(capability, range.maxQuantity)}><Trash /></button>
              </div>
            })}</div>
          </div>
        </article>
      })}</div>
    </section>
    {(validation || data.save.isError) && <p className="processor-profile-message processor-profile-error" role="alert">{validation || toErrorMessage(data.save.error)}</p>}
    {saved && <p className="processor-profile-message processor-profile-success" role="status"><CheckCircle weight="fill" />가공업체 정보가 저장되었어요.</p>}
    <footer className="processor-profile-actions"><p>지역과 취급 조건이 등록되면 업체 검색에 즉시 노출됩니다.</p><button type="button" onClick={() => void submit()} disabled={data.save.isPending}>{data.save.isPending ? '저장 중…' : '가공업체 정보 저장'}</button></footer>
  </div>
}
