import type { ProcessType } from '../../processing/types/processing.types.ts'
import type { MaterialGenerationType } from '../types/material.types.ts'
import { processingHistoryText } from '../utils/processingHistory.ts'
import './material-processing-badge.css'

/*
  이력을 모르면(아직 이 값을 싣지 않는 서버) 아무것도 그리지 않는다 — 빈 목록과 달리 "가공
  전" 이라고 말할 근거가 없다.
*/
export function MaterialProcessingBadge({ completedProcessTypes, generationType }: {
  completedProcessTypes: readonly ProcessType[] | null | undefined
  generationType?: MaterialGenerationType
}) {
  if (!completedProcessTypes) return null
  const processed = completedProcessTypes.length > 0
  return (
    <span className={`material-processing-badge ${processed ? 'is-processed' : 'is-raw'}`}>
      {processingHistoryText(completedProcessTypes, generationType)}
    </span>
  )
}
