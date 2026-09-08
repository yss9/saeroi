import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProductThumbnail } from '../api/getProductThumbnail.ts'
import { productKeys } from '../api/productKeys.ts'

export interface UseProductThumbnailResult {
  /** `<img src>` 에 넣을 blob URL. 아직 못 받았으면 null */
  url: string | null
  isPending: boolean
  /** 이미지가 없거나 받지 못함. 화면은 자리표시자로 처리한다 */
  isError: boolean
}

/**
 * 인증이 필요한 제품 이미지를 화면에 쓸 수 있는 주소로 바꾼다.
 *
 * **Blob 을 캐시하고 blob URL 은 컴포넌트가 만든다.** 반대로 하면(URL 을 캐시에 넣으면)
 * 한 컴포넌트가 사라질 때 revoke 한 주소를 다른 컴포넌트가 계속 들고 있게 되고, 그 이미지는
 * 조용히 깨진다. Blob 은 여러 곳에서 안전하게 공유된다.
 *
 * 재시도하지 않는다 — 이미지가 없는 제품(`PRODUCT_THUMBNAIL_NOT_FOUND`)은 다시 물어도
 * 같은 답이고, 이 화면에서 이미지 부재는 실패가 아니라 하나의 상태다.
 */
export function useProductThumbnail(productModelId: number | null): UseProductThumbnailResult {
  const query = useQuery<Blob, AppError>({
    queryKey: [...productKeys.detail(productModelId ?? 0), 'thumbnail'],
    queryFn: () => getProductThumbnail(productModelId as number),
    enabled: productModelId !== null,
    retry: false,
  })

  // 주소는 렌더 중에 만들고 해제만 effect 에서 한다.
  //
  // effect 에서 만들어 setState 하면 데이터를 받은 뒤 한 프레임 동안 "받았는데 주소가 없는"
  // 상태가 생겨 화면이 깜빡이고, 렌더를 한 번 더 유발한다 (react-hooks/set-state-in-effect
  // 가 막는 것도 이것이다). Blob 이 같으면 같은 주소를 계속 쓴다.
  const url = useMemo(() => (query.data ? URL.createObjectURL(query.data) : null), [query.data])

  // blob URL 은 브라우저가 들고 있는 자원이라 명시적으로 해제해야 한다. 외부 시스템과의
  // 동기화이므로 여기가 useEffect 의 정당한 용도다.
  useEffect(() => {
    if (url === null) return
    return () => URL.revokeObjectURL(url)
  }, [url])

  return { url, isPending: query.isPending, isError: query.isError }
}
