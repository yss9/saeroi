import { useEffect, useMemo } from 'react'

/**
 * `Blob`(또는 `File`)을 `<img src>` · `<a href>` 에 쓸 수 있는 blob URL 로 바꾼다.
 *
 * <h2>왜 훅이 필요한가</h2>
 *
 * <p>`URL.createObjectURL` 은 **명시적으로 해제해야** 브라우저 메모리에서 사라진다.
 * 렌더 중에 만들면 리렌더마다 새 URL 이 쌓이고 아무도 해제하지 않는다. 브라우저의 URL
 * 저장소라는 외부 시스템과의 동기화이므로 `useEffect` 가 맞는 자리다
 * (ARCHITECTURE.md §3).
 *
 * <h2>어디에 쓰이는가</h2>
 *
 * <p>두 경우가 있고 둘 다 같은 이유로 blob 이 필요하다.
 *
 * <ul>
 *   <li><b>업로드 전</b> — 사용자가 고른 파일. 서버에 아직 없으므로 브라우저가 가진
 *       바이트로 그린다
 *   <li><b>업로드 후</b> — 서버 파일. 조회 경로가 <b>Authorization 헤더</b>를 요구하는데
 *       브라우저가 스스로 보내는 `<img src>` 요청에는 그 헤더가 붙지 않는다. 그래서
 *       `apiClient` 로 받아 온 blob 을 여기서 URL 로 바꿔 그린다
 * </ul>
 *
 * <h2>왜 useState 가 아니라 useMemo 인가</h2>
 *
 * <p>effect 에서 만들어 `setState` 하면 **첫 렌더가 URL 없이 한 번 지나간다.** 그 프레임에
 * `<img src="">` 를 그리면 빈 요청이 나가고, 피하려면 호출부마다 "아직 없음" 분기를
 * 두어야 한다. 렌더 중에 URL 을 만들면 그 분기가 필요 없다. 해제는 effect 정리에서
 * 하므로 누수도 없다.
 *
 * <p>대가는 있다 — `useMemo` 는 캐시일 뿐이라 React 가 계산을 한 번 더 돌리면 URL 이
 * 하나 더 생기고 그 하나는 해제되지 않는다. 페이지 수명 동안의 blob 하나이고,
 * `setState` 경로의 빈 요청보다 눈에 덜 띄는 대가라고 판단했다.
 */
export function useObjectUrl(source: Blob | null | undefined): string | null {
  const url = useMemo(() => (source ? URL.createObjectURL(source) : null), [source])

  useEffect(
    () => () => {
      if (url !== null) URL.revokeObjectURL(url)
    },
    [url],
  )

  return url
}
