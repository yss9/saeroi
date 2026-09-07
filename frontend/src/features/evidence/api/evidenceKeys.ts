import { createQueryKeys } from '../../../utils/queryKeys.ts'

const keys = createQueryKeys('evidence')

/**
 * 증빙 캐시 키.
 *
 * <b>대상 종류를 키에 반드시 넣는다.</b> `reference_id` 는 타입마다 따로 매겨지므로
 * id 만으로 키를 만들면 7번 소재의 증빙과 7번 가공작업의 증빙이 같은 캐시를 쓴다.
 */
export const evidenceKeys = {
  ...keys,
  list: (referenceType: string, referenceId: number) =>
    [...keys.lists(), referenceType, referenceId] as const,
}
