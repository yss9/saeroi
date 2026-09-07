import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { processingRequestApi, type CreatedProcessingRequest, type ProcessorMatch, type ProcessorSearch } from '../api/processingRequestApi.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import type { ProcessType } from '../types/processing.types.ts'

export function useBrandProcessingRequest() {
  const client = useQueryClient()
  const [step, setStep] = useState(1)
  const [materialId, setMaterialId] = useState('')
  const [quantityText, setQuantityText] = useState('')
  const [processTypes, setProcessTypes] = useState<ProcessType[]>([])
  const [filters, setFilters] = useState<ProcessorSearch | null>(null)
  const [processor, setProcessor] = useState<ProcessorMatch | null>(null)
  const [created, setCreated] = useState<CreatedProcessingRequest | null>(null)
  const submitting = useRef(false)
  const materials = useQuery({ queryKey: processingKeys.list({ ownedMaterials: true }), queryFn: processingRequestApi.materials })
  const material = materials.data?.find(item => String(item.materialId) === materialId)
  const quantity = Number(quantityText)
  const quantityError = !quantityText ? '' : !/^\d+$/.test(quantityText) || !Number.isSafeInteger(quantity) || quantity <= 0
    ? '수량은 1 g 이상의 정수로 입력해 주세요.'
    : material && quantity > material.availableQuantity ? '가용 수량을 초과할 수 없어요.' : ''
  const valid = Boolean(material && quantityText && !quantityError && processTypes.length)
  const search = useQuery({ queryKey: processingKeys.list({ processors: filters }), queryFn: ({ signal }) => processingRequestApi.search(filters!, signal), enabled: filters !== null })
  const refresh = async () => {
    await Promise.all([client.invalidateQueries({ queryKey: processingKeys.all }), client.invalidateQueries({ queryKey: materialKeys.all })])
  }
  const create = useMutation({ mutationFn: processingRequestApi.create, onSuccess: (data) => { setCreated(data); void refresh() }, onError: () => { void refresh() } })
  const cancel = useMutation({ mutationFn: processingRequestApi.cancel, onSuccess: (status) => { setCreated(current => current ? { ...current, status } : current); void refresh() } })
  function clearSelection() { setFilters(null); setProcessor(null); create.reset() }
  return {
    step, materialId, quantityText, processTypes, material, quantity, quantityError, valid, materials, search, processor, created, create, cancel,
    changeMaterial: (value: string) => { setMaterialId(value); clearSelection() },
    changeQuantity: (value: string) => { setQuantityText(value); clearSelection() },
    toggleType: (type: ProcessType) => { setProcessTypes(current => current.includes(type) ? current.filter(value => value !== type) : [...current, type]); clearSelection() },
    findProcessors: () => { if (valid && material) { setProcessor(null); setFilters({ materialType: material.materialType, quantity, processTypes, page: 0, size: 12 }); setStep(2) } },
    selectProcessor: setProcessor,
    edit: () => { setStep(1); clearSelection() },
    confirm: () => { if (processor && valid) setStep(3) },
    backToSearch: () => { setStep(2); create.reset() },
    changePage: (page: number) => { setProcessor(null); setFilters(current => current ? { ...current, page } : null) },
    submit: async () => {
      if (!valid || !material || !processor || submitting.current || created) return
      submitting.current = true
      try { await create.mutateAsync({ materialId: material.materialId, processorId: processor.processorId, quantity, processTypes }) } catch { /* Error is rendered by the component. */ } finally { submitting.current = false }
    },
    cancelRequest: () => { if (created && !cancel.isPending) cancel.mutate(created.requestId) },
    restart: () => { setCreated(null); setStep(1); setQuantityText(''); setProcessTypes([]); clearSelection(); cancel.reset() },
  }
}
