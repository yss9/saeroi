// 앱 전역 QueryClient.
//
// AppProviders 에서 QueryClientProvider 에 연결한다. 테스트에서는 격리를 위해
// 각 테스트마다 새 QueryClient 를 만들어 쓰는 편이 낫다 (retry: false 로).

import { QueryClient } from '@tanstack/react-query'
import { isAppError } from '../utils/errors.ts'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 목록/상세가 화면 전환마다 다시 요청되지 않도록 짧게 신선도를 준다.
      staleTime: 30_000,
      // 인증·권한·404 는 다시 시도해도 결과가 같다. 재시도는 낭비이고 401 을
      // 여러 번 유발해 로그아웃 처리가 반복된다.
      retry: (failureCount, error) => {
        if (isAppError(error) && error.status !== null && error.status < 500) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      // 쓰기 요청은 자동 재시도하지 않는다. 중복 생성 위험이 있고,
      // 블록체인 트랜잭션은 특히 되돌릴 수 없다.
      retry: false,
    },
  },
})
