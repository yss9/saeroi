import { PROCESS_TYPE_LABEL, type ProcessType } from '../../processing/types/processing.types.ts'
import type { MaterialGenerationType } from '../types/material.types.ts'

/*
  가공을 거쳐도 소재 이름은 바뀌지 않는다 — 같은 소재를 단계마다 다른 이름으로 부르면 여정을
  따라가는 사람이 그게 같은 소재인지 알 수 없다. 그 대신 이름이 같은 소재를 가르는 것이 이
  배지다: 가공 전인지, 어떤 가공을 거쳤는지. 값은 서버가 혈통을 거슬러 확정된 가공만 모은
  completedProcessTypes 이고, 보유 목록·상세·마켓·선택지가 모두 같은 말을 쓴다.
*/
export function processingHistoryText(
  completedProcessTypes: readonly ProcessType[],
  generationType?: MaterialGenerationType,
): string {
  const history = completedProcessTypes.length === 0
    ? '가공 전'
    : `${completedProcessTypes.map(type => PROCESS_TYPE_LABEL[type]).join('·')} 가공`
  return generationType === 'PRODUCTION_SCRAP' ? `생산 자투리 · ${history}` : history
}
