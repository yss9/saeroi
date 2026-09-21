import { useLayoutEffect, useRef } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

function hashTarget(hash: string): HTMLElement | null {
  if (!hash.startsWith('#') || hash.length === 1) return null
  try {
    return document.getElementById(decodeURIComponent(hash.slice(1)))
  } catch {
    return null
  }
}

/** 새 경로는 위에서 시작하고, 같은 화면의 쿼리·해시 상태 변경은 각 화면에 맡긴다. */
export function ScrollOnNavigate() {
  const { pathname, hash, key, state } = useLocation()
  const previousPathname = useRef<string | null>(null)
  const skippedEntryKeys = useRef(new Set<string>())

  useLayoutEffect(() => {
    if (previousPathname.current === pathname) return
    previousPathname.current = pathname

    const skipRequested = Boolean(
      state && typeof state === 'object' && 'skipGlobalScrollReset' in state &&
      state.skipGlobalScrollReset === true,
    )
    if (skipRequested && !skippedEntryKeys.current.has(key)) {
      skippedEntryKeys.current.add(key)
      return
    }

    if (!hash) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
      return
    }

    const scrollToHash = () => {
      const target = hashTarget(hash)
      if (!target) return false
      target.scrollIntoView({ behavior: 'auto', block: 'start' })
      return true
    }

    if (scrollToHash()) return

    // 보호된 화면이나 API 기반 화면은 경로가 바뀐 직후 앵커가 아직 렌더되지 않을 수 있다.
    const observer = new MutationObserver(() => {
      if (scrollToHash()) observer.disconnect()
    })
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [hash, key, pathname, state])

  return <Outlet />
}
