// Query Key 생성 규칙.
//
// 왜 굳이 팩토리를 두는가 —
// 각 도메인이 `['materials']`, `['material-list']`, `'materials'` 처럼 제각각 키를
// 지으면 무효화(invalidateQueries)가 조용히 빗나간다. 등록에 성공했는데 목록이
// 갱신되지 않는 버그가 대부분 여기서 나온다.
//
// 규칙은 계층형이다. 상위 키로 무효화하면 하위가 전부 함께 무효화된다.
//
//   all      → ['material']
//   lists()  → ['material', 'list']
//   list(f)  → ['material', 'list', { status: 'ACTIVE' }]
//   details()→ ['material', 'detail']
//   detail(1)→ ['material', 'detail', 1]
//
// 사용:
//   export const materialKeys = createQueryKeys('material')
//   queryClient.invalidateQueries({ queryKey: materialKeys.lists() })  // 목록만
//   queryClient.invalidateQueries({ queryKey: materialKeys.all })      // 도메인 전체

export interface QueryKeyFactory<TScope extends string> {
  readonly all: readonly [TScope]
  lists: () => readonly [TScope, 'list']
  list: <TFilters extends object>(filters?: TFilters) => readonly unknown[]
  details: () => readonly [TScope, 'detail']
  detail: (id: string | number) => readonly [TScope, 'detail', string | number]
}

export function createQueryKeys<TScope extends string>(scope: TScope): QueryKeyFactory<TScope> {
  const all = [scope] as const
  return {
    all,
    lists: () => [scope, 'list'] as const,
    // filters 가 없으면 lists() 와 같은 키가 되도록 배열에 넣지 않는다.
    // 빈 객체를 넣으면 `['material','list',{}]` 가 되어 lists() 로 무효화해도
    // 실제 캐시와 맞지 않는 일이 생긴다.
    list: (filters) => (filters ? ([scope, 'list', filters] as const) : ([scope, 'list'] as const)),
    details: () => [scope, 'detail'] as const,
    detail: (id) => [scope, 'detail', id] as const,
  }
}
