import { createQueryKeys } from '../../../utils/queryKeys.ts'

/**
 * Material 도메인의 Query Key.
 *
 * 이 파일 밖에서 `['material', ...]` 배열을 직접 만들지 않는다.
 * 등록/수정 후 `queryClient.invalidateQueries({ queryKey: materialKeys.lists() })`
 * 처럼 여기 함수를 통해서만 참조한다.
 */
export const materialKeys = createQueryKeys('material')
