import { CheckCircle, Clock, Factory, MapPin, Plus, Trash } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { toErrorMessage } from '../../../utils/errors.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import { MATERIAL_TYPE_LABEL, type MaterialType, type ProcessorCapability, type ProcessorLeadTime } from '../types/processorProfile.types.ts'
import { useProcessorProfile } from '../hooks/useProcessorProfile.ts'
import './processor-profile.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
const processTypes = Object.keys(PROCESS_TYPE_LABEL) as ProcessType[]
const emptyCapability = (): ProcessorCapability => ({ materialType: 'DENIM', processType: 'CLEANING', minQuantity: 1, maxQuantity: 1 })
const emptyLeadTime = (): ProcessorLeadTime => ({ maxQuantity: 1, leadDays: 1 })

export function ProcessorProfileForm() {
  const data = useProcessorProfile()
  const initialized = useRef(false)
  const [region, setRegion] = useState('')
  const [capabilities, setCapabilities] = useState<ProcessorCapability[]>([emptyCapability()])
  const [leadTimes, setLeadTimes] = useState<ProcessorLeadTime[]>([emptyLeadTime()])
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState(false)
  const loading = data.profile.isPending || data.capabilities.isPending || data.leadTimes.isPending
  const loadError = data.profile.isError || data.capabilities.isError || data.leadTimes.isError

  useEffect(() => {
    if (initialized.current || loading || loadError) return
    initialized.current = true
    setRegion(data.profile.data?.region ?? '')
    setCapabilities(data.capabilities.data?.length ? data.capabilities.data : [emptyCapability()])
    setLeadTimes(data.leadTimes.data?.length ? data.leadTimes.data : [emptyLeadTime()])
  }, [data.capabilities.data, data.leadTimes.data, data.profile.data, loadError, loading])

  function updateCapability(index: number, patch: Partial<ProcessorCapability>) {
    setCapabilities(rows => rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row))
  }
  function updateLeadTime(index: number, patch: Partial<ProcessorLeadTime>) {
    setLeadTimes(rows => rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row))
  }
  function validate() {
    if (!region.trim()) return '지역을 입력해 주세요.'
    if (capabilities.some(row => !Number.isInteger(row.minQuantity) || !Number.isInteger(row.maxQuantity) || row.minQuantity < 1 || row.maxQuantity < row.minQuantity)) return '소재·공정별 수량은 1 g 이상의 정수이며 최소 수량이 최대 수량보다 클 수 없어요.'
    if (new Set(capabilities.map(row => `${row.materialType}:${row.processType}`)).size !== capabilities.length) return '동일한 소재·공정 조합은 한 번만 등록할 수 있어요.'
    if (leadTimes.some(row => !Number.isInteger(row.maxQuantity) || !Number.isInteger(row.leadDays) || row.maxQuantity < 1 || row.leadDays < 1)) return '소요일 구간과 일수는 양의 정수로 입력해 주세요.'
    if (new Set(leadTimes.map(row => row.maxQuantity)).size !== leadTimes.length) return '소요일 구간의 최대 수량은 중복할 수 없어요.'
    if (Math.max(...leadTimes.map(row => row.maxQuantity)) < Math.max(...capabilities.map(row => row.maxQuantity))) return '마지막 소요일 구간이 모든 가공 조건의 최대 수량을 포함해야 해요.'
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
        leadTimes: { leadTimes: [...leadTimes].sort((a, b) => a.maxQuantity - b.maxQuantity) },
      })
      setSaved(true)
    } catch { /* mutation error is rendered below */ }
  }

  if (loading) return <div className="processor-profile-state" role="status">업체 정보를 불러오는 중이에요…</div>
  if (loadError) return <div className="processor-profile-state processor-profile-error" role="alert">{toErrorMessage(data.profile.error ?? data.capabilities.error ?? data.leadTimes.error)}</div>

  return <div className="processor-profile-form">
    <header className="processor-profile-heading"><div><span>PROCESSOR SETTINGS</span><h1>가공업체 정보 등록</h1><p>처리 가능한 소재와 공정, 수량별 예상 소요일을 등록하면 Brand 검색에 노출돼요.</p></div><Factory size={36} weight="duotone" /></header>
    <section className="processor-profile-card">
      <div className="processor-profile-title"><MapPin size={23} /><div><h2>업체 지역</h2><p>Brand가 가공업체를 비교할 때 표시되는 지역이에요.</p></div></div>
      <label className="processor-profile-field"><span>지역</span><input value={region} maxLength={100} onChange={event => setRegion(event.target.value)} placeholder="예: 서울 성동구" /></label>
    </section>
    <section className="processor-profile-card">
      <div className="processor-profile-section-head"><div className="processor-profile-title"><Factory size={23} /><div><h2>취급 소재·공정</h2><p>각 조합마다 실제로 받을 수 있는 최소·최대 수량을 g 단위로 입력하세요.</p></div></div><button type="button" className="processor-profile-secondary" onClick={() => setCapabilities(rows => [...rows, emptyCapability()])}><Plus />조건 추가</button></div>
      <div className="processor-profile-table"><div className="processor-profile-table-head"><span>소재</span><span>공정</span><span>최소 수량</span><span>최대 수량</span><span /></div>{capabilities.map((row, index) => <div className="processor-profile-row" key={index}>
        <label><span>소재 {index + 1}</span><select value={row.materialType} onChange={event => updateCapability(index, { materialType: event.target.value as MaterialType })}>{materialTypes.map(type => <option key={type} value={type}>{MATERIAL_TYPE_LABEL[type]}</option>)}</select></label>
        <label><span>공정 {index + 1}</span><select value={row.processType} onChange={event => updateCapability(index, { processType: event.target.value as ProcessType })}>{processTypes.map(type => <option key={type} value={type}>{PROCESS_TYPE_LABEL[type]}</option>)}</select></label>
        <label><span>최소 수량 {index + 1}</span><div className="processor-profile-unit"><input aria-label={`최소 수량 ${index + 1}`} type="number" min="1" step="1" value={row.minQuantity} onChange={event => updateCapability(index, { minQuantity: Number(event.target.value) })} /><b>g</b></div></label>
        <label><span>최대 수량 {index + 1}</span><div className="processor-profile-unit"><input aria-label={`최대 수량 ${index + 1}`} type="number" min="1" step="1" value={row.maxQuantity} onChange={event => updateCapability(index, { maxQuantity: Number(event.target.value) })} /><b>g</b></div></label>
        <button type="button" aria-label={`가공 조건 ${index + 1} 삭제`} disabled={capabilities.length === 1} onClick={() => setCapabilities(rows => rows.filter((_, rowIndex) => rowIndex !== index))}><Trash /></button>
      </div>)}</div>
    </section>
    <section className="processor-profile-card">
      <div className="processor-profile-section-head"><div className="processor-profile-title"><Clock size={23} /><div><h2>수량 구간별 소요일</h2><p>요청 수량 이상을 포함하는 가장 작은 구간의 평균 작업 기간이 검색 결과에 표시돼요.</p></div></div><button type="button" className="processor-profile-secondary" onClick={() => setLeadTimes(rows => [...rows, emptyLeadTime()])}><Plus />구간 추가</button></div>
      <div className="processor-profile-lead-list">{leadTimes.map((row, index) => <div className="processor-profile-lead" key={index}><span>{index + 1}구간</span><label><span>최대 수량</span><div className="processor-profile-unit"><input aria-label={`구간 최대 수량 ${index + 1}`} type="number" min="1" step="1" value={row.maxQuantity} onChange={event => updateLeadTime(index, { maxQuantity: Number(event.target.value) })} /><b>g</b></div></label><label><span>평균 소요일</span><div className="processor-profile-unit"><input aria-label={`평균 소요일 ${index + 1}`} type="number" min="1" step="1" value={row.leadDays} onChange={event => updateLeadTime(index, { leadDays: Number(event.target.value) })} /><b>일</b></div></label><button type="button" aria-label={`소요일 구간 ${index + 1} 삭제`} disabled={leadTimes.length === 1} onClick={() => setLeadTimes(rows => rows.filter((_, rowIndex) => rowIndex !== index))}><Trash /></button></div>)}</div>
    </section>
    {(validation || data.save.isError) && <p className="processor-profile-message processor-profile-error" role="alert">{validation || toErrorMessage(data.save.error)}</p>}
    {saved && <p className="processor-profile-message processor-profile-success" role="status"><CheckCircle weight="fill" />가공업체 정보가 저장되었어요.</p>}
    <footer className="processor-profile-actions"><p>세 항목이 모두 등록되면 업체 검색에 즉시 노출됩니다.</p><button type="button" onClick={() => void submit()} disabled={data.save.isPending}>{data.save.isPending ? '저장 중…' : '가공업체 정보 저장'}</button></footer>
  </div>
}
