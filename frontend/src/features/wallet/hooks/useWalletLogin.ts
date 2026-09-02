// 지갑 서명 기반 로그인.
//
// 전체 흐름 (Backend 와 합의된 순서):
//
//   MetaMask 연결
//        ↓
//   Wallet Address 획득
//        ↓
//   POST /auth/wallet/nonce      → Backend 가 1회용 nonce 발급 (wallet_nonce 테이블)
//        ↓
//   nonce 로 서명 메시지 구성
//        ↓
//   signer.signMessage()          → 사용자가 MetaMask 에서 서명
//        ↓
//   POST /auth/wallet/verify      → Backend 가 서명에서 주소를 복원해 대조
//        ↓
//   JWT 발급 → authStore.signIn()
//
// ⚠️ 현재 Backend 에 해당 엔드포인트가 아직 없다. 아래 mutation 은 구조만 잡아둔
// 상태이며, `TODO(6번/User·Wallet 담당)` 표시가 붙은 두 곳을 실제 API 호출로
// 바꾸면 그대로 동작한다. 나머지 배선(서명·에러 처리·스토어 반영)은 완성되어 있다.

import { useMutation } from '@tanstack/react-query'
import { useAuthStore, type AuthUser } from '../../../stores/authStore.ts'
import { createAppError, ERROR_KIND, type AppError } from '../../../utils/errors.ts'
import { getWalletAddress, signMessage } from '../services/walletService.ts'

/**
 * 서명할 메시지를 만든다.
 *
 * 사람이 읽고 무엇에 서명하는지 판단할 수 있어야 한다 (MetaMask 가 이 문자열을 그대로 보여준다).
 * nonce 를 포함해 같은 서명을 재사용하는 replay 공격을 막는다.
 *
 * TODO(6번): Backend 의 검증 로직과 **문자열이 한 글자도 다르면 안 된다**.
 * 형식을 확정한 뒤 양쪽 상수를 같은 문서에 기록한다.
 */
/** 서명 검증 성공 시 Backend 가 돌려주는 것. */
export interface WalletLoginResult {
  user: AuthUser
  accessToken: string
}

export function buildLoginMessage(address: string, nonce: string): string {
  return [
    'SAEROI 로그인 요청',
    '',
    `지갑 주소: ${address}`,
    `Nonce: ${nonce}`,
    '',
    '이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.',
  ].join('\n')
}

export function useWalletLogin() {
  const signIn = useAuthStore((state) => state.signIn)

  return useMutation<WalletLoginResult, AppError>({
    mutationFn: async () => {
      const address = await getWalletAddress()
      if (!address) throw createAppError(ERROR_KIND.WALLET_DISCONNECTED)

      // TODO(6번/User·Wallet 담당): 실제 nonce 발급 API 로 교체
      //   const { nonce } = await requestWalletNonce(address)
      const nonce = 'TODO-BACKEND-NONCE'

      // 서명 취소(4001)는 walletService 가 SIGNATURE_REJECTED 로 정규화해 던진다.
      // 여기서 따로 잡지 않고 호출부가 error.kind 로 분기하게 둔다.
      const signature = await signMessage(buildLoginMessage(address, nonce))

      // TODO(6번/User·Wallet 담당): 서명 검증 + JWT 발급 API 로 교체하면
      // 아래 throw 를 지우고 `return await verifyWalletSignature({ address, signature })` 로 바꾼다.
      // 그 순간부터 onSuccess 의 signIn 이 그대로 동작한다.
      throw createAppError(ERROR_KIND.API_ERROR, {
        message: '지갑 로그인 API가 아직 준비되지 않았습니다.',
        cause: { address, signature },
      })
    },
    onSuccess: ({ user, accessToken }) => signIn(user, accessToken),
  })
}
