import { useCallback, type MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'

const INTERACTIVE_CHILDREN = 'a, button, input, select, textarea, label, [role="button"]'

/** 카드·표 행의 빈 영역을 클릭했을 때만 상세 화면으로 이동한다. */
export function useClickNavigation() {
  const navigate = useNavigate()

  return useCallback((event: MouseEvent<HTMLElement>, to: string) => {
    const target = event.target
    if (target instanceof Element && target.closest(INTERACTIVE_CHILDREN)) return
    void navigate(to)
  }, [navigate])
}
