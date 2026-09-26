import { useSyncExternalStore } from 'react'

/**
 * 대화창이 <b>두 단</b>으로 그려지는 폭인가.
 *
 * `agent.css` 의 `@media (max-width: 780px)` 와 같은 값이다. 그 폭 아래에서는 오른쪽 패널이
 * 대화 **아래**로 내려간다.
 *
 * 떠 있는 카드를 어디에 그릴지가 여기 달렸다. 넓은 화면에서는 오른쪽에 고정해 두면 대화가
 * 흘러도 카드가 떠내려가지 않는다. 그런데 좁은 화면에서 같은 자리에 두면 **대화 전체를
 * 지나쳐야** 카드에 닿아 지금보다 나빠진다. 그래서 그때는 대화 안에 그대로 둔다.
 *
 * 두 값이 어긋나면 카드가 양쪽에 다 보이거나 어느 쪽에도 안 보인다. CSS 를 고치면 여기도
 * 고쳐야 한다.
 */
const TWO_COLUMN_MIN_WIDTH = '(min-width: 781px)'

/**
 * 창 크기를 바꾸는 도중에도 맞아야 한다. 카드가 한쪽에만 있어야 하므로 어긋난 순간 담당자는
 * 눌러야 할 것을 못 찾는다. 미디어 쿼리를 외부 상태로 구독한다 — effect 안에서 상태를 맞추면
 * 첫 그림이 한 번 더 그려진다.
 */
export function useWideLayout(): boolean {
  return useSyncExternalStore(subscribe, matches, () => false)
}

function subscribe(onChange: () => void): () => void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return () => {}
  }
  const query = window.matchMedia(TWO_COLUMN_MIN_WIDTH)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

function matches(): boolean {
  // 테스트 환경(jsdom)과 서버 렌더에는 matchMedia 가 없을 수 있다. 없으면 한 단으로 본다 —
  // 카드가 대화 안에 남는 쪽이 안전하다. 오른쪽 패널이 없는 화면에서 그쪽에 그리면 카드가
  // 아예 보이지 않는다.
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia(TWO_COLUMN_MIN_WIDTH).matches
}
