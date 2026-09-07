import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * Material 도메인의 Query Key.
 *
 * 이 파일 밖에서 `['material', ...]` 배열을 직접 만들지 않는다.
 * 등록/수정 후 `queryClient.invalidateQueries({ queryKey: materialKeys.lists() })`
 * 처럼 여기 함수를 통해서만 참조한다.
 */
const keys = createQueryKeys('material')

export const materialKeys = {
  ...keys,

  /**
   * 소재의 보유자별 잔량.
   *
   * **`detail(id)` 아래에 둔다.** 그래야 거래·가공으로 소재가 바뀔 때
   * `invalidateQueries({ queryKey: materialKeys.detail(id) })` 한 번으로
   * 상세와 잔량이 함께 갱신된다. 별도 최상위 키로 두면 둘 중 하나만 갱신되어
   * "상세는 새 값, 잔량은 옛 값" 인 화면이 나온다.
   */
  balance: (materialId: string | number) => [...keys.detail(materialId), 'balance'] as const,
} as const
