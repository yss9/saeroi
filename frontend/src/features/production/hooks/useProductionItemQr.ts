import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProductionItemQr } from '../api/productionBatchApi.ts'
import { productionItemKeys } from '../api/productionKeys.ts'

export interface UseProductionItemQrResult {
  /** `<img src>` 에 넣을 blob URL. 아직 못 받았으면 null */
  url: string | null
  isPending: boolean
  isError: boolean
  /** 실패 문구. 서버 문구를 쓸 수 없어 상태 코드로 만든다 */
  errorMessage: string | null
  retry: () => void
}

/**
 * 개체 하나의 QR 이미지.
 *
 * **Blob 을 캐시하고 blob URL 은 화면이 만든다.** 반대로 하면(URL 을 캐시에 넣으면) 한
 * 컴포넌트가 사라지며 revoke 한 주소를 다른 컴포넌트가 계속 들고 있게 되고, 그 이미지는
 * 조용히 깨진다 — `useProductThumbnail` 이 같은 판단을 한다.
 *
 * **`enabled` 로 지연시킨다.** 배치 하나에 개체가 수십 개다. 목록을 열자마자 전부 받으면
 * 보지도 않을 PNG 수십 장을 내려받는다. 사용자가 그 개체의 QR 을 열 때 처음 받는다.
 *
 * **한 번 받으면 다시 받지 않는다** (`staleTime: Infinity`). 토큰이 바뀌지 않는 한 같은
 * 개체의 QR 은 영원히 같은 이미지이고, 서버도 `immutable` 로 캐시하라고 응답한다.
 */
export function useProductionItemQr(
  batchId: number,
  productItemId: number,
  enabled: boolean,
): UseProductionItemQrResult {
  const query = useQuery<Blob, AppError>({
    queryKey: productionItemKeys.qr(batchId, productItemId),
    queryFn: () => getProductionItemQr(batchId, productItemId),
    enabled,
    staleTime: Infinity,
    retry: false,
  })

  // 주소는 렌더 중에 만들고 해제만 effect 에서 한다. effect 에서 만들어 setState 하면
  // 받은 뒤 한 프레임 동안 "받았는데 주소가 없는" 상태가 생겨 화면이 깜빡인다.
  const url = useMemo(() => (query.data ? URL.createObjectURL(query.data) : null), [query.data])

  // blob URL 은 브라우저가 들고 있는 자원이라 명시적으로 해제한다. 외부 자원과의
  // 동기화이므로 여기가 useEffect 의 정당한 용도다.
  useEffect(() => {
    if (url === null) return
    return () => URL.revokeObjectURL(url)
  }, [url])

  return {
    url,
    isPending: enabled && query.isPending,
    isError: query.isError,
    errorMessage: query.isError ? messageFor(query.error) : null,
    retry: () => void query.refetch(),
  }
}

/**
 * 실패 문구.
 *
 * ⚠️ **서버가 준 문구를 쓸 수 없다.** `responseType: 'blob'` 이면 에러 응답의 본문도 Blob 으로
 * 와서 `apiClient` 가 봉투(`code`·`message`)를 읽지 못한다 — 상태 코드만 남는다.
 * 그래서 여기서 만든다. 문구 없이 "알 수 없는 오류" 만 띄우면 브랜드는 라벨을 못 뽑는
 * 이유를 알 수 없다.
 */
function messageFor(error: AppError | null): string {
  switch (error?.status) {
    case 403:
      return '이 배치의 QR 을 만들 권한이 없습니다. 배치를 연 계정으로 로그인해 주세요.'
    case 404:
      return '개체를 찾을 수 없습니다. 목록을 새로 고친 뒤 다시 시도해 주세요.'
    default:
      return 'QR 이미지를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.'
  }
}
