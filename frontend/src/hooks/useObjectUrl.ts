import { useEffect, useState } from 'react'

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
 * <h2>왜 effect 에서 만드는가</h2>
 *
 * <p>생성과 해제를 같은 effect 에 두면 마운트마다 만든 URL을 정확히 그 마운트의 정리에서
 * 해제할 수 있다. 개발 모드의 StrictMode가 effect를 정리한 뒤 다시 실행해도 이미 해제된
 * URL을 재사용하지 않고 새 URL을 만든다. URL이 준비되기 전에는 `null`을 반환하므로
 * 호출부는 그동안 `<img>` 자체를 그리지 않아 빈 `src` 요청을 막는다.
 */
export function useObjectUrl(source: Blob | null | undefined): string | null {
  const [created, setCreated] = useState<{ source: Blob; url: string } | null>(null)

  useEffect(() => {
    if (!source) {
      // 파일을 제거한 뒤에도 이전 Blob을 상태가 붙잡아 두지 않는다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCreated(null)
      return
    }

    const url = URL.createObjectURL(source)
    // Blob URL은 브라우저 외부 자원이라 effect에서 생성한 뒤 렌더에 전달해야 한다.
    setCreated({ source, url })
    return () => URL.revokeObjectURL(url)
  }, [source])

  // source가 바뀐 직후 effect가 새 URL을 만들기 전에는 이전 source의 URL을 노출하지 않는다.
  return created && created.source === source ? created.url : null
}
