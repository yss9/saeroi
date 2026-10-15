// 지갑 서명 기반 로그인.
//
// 전체 흐름 (Backend 와 확정된 순서):
//
//   MetaMask 연결
//        ↓
//   Wallet Address · Chain ID 획득
//        ↓
//   POST /v1/auth/wallet/nonce   → Backend 가 1회용 nonce 와 서명 정본 메시지를 준다
//        ↓
//   메시지 대조 후 personal_sign  → 사용자가 MetaMask 에서 서명
//        ↓
//   POST /v1/auth/wallet/verify  → Backend 가 서명에서 주소를 복원해 대조
//        ↓
//   JWT 발급 → authStore.signIn()
//
// 미등록 지갑은 nonce 단계에서 404(WALLET_NOT_LINKED)로 걸러진다 — 서명 팝업이 뜨기 전이다.
// 안내 문구는 이 훅이 정하지 않는다. 호출부(WalletLoginButton)가 error.code 로 고른다.

import { useMutation } from '@tanstack/react-query'
import { useAuthStore, type AuthUser } from '../../../stores/authStore.ts'
import { createAppError, ERROR_KIND, type AppError } from '../../../utils/errors.ts'
import { requestWalletNonce, verifyWalletSignature } from '../../auth/api/walletAuthApi.ts'
import type { UserProfile } from '../../auth/types/auth.types.ts'
import { buildLoginMessage } from '../services/walletMessage.ts'
import { getChainIdNumber, getWalletAddress, signMessage } from '../services/walletService.ts'

/** 서명 검증 성공 시 Backend 가 돌려주는 것. */
export interface WalletLoginResult {
  user: AuthUser
  accessToken: string
}

/** 이메일 로그인(useLogin)과 달리 방금 검증한 주소를 그대로 채운다. */
function toAuthUser(profile: UserProfile, walletAddress: string): AuthUser {
  return {
    id: profile.id,
    email: profile.email,
    name: profile.name,
    role: profile.role,
    walletAddress,
  }
}

export function useWalletLogin() {
  const signIn = useAuthStore((state) => state.signIn)

  return useMutation<WalletLoginResult, AppError>({
    mutationFn: async () => {
      // 스토어의 주소가 아니라 provider 에서 직접 읽는다. 서명을 만들 signer 와 같은
      // 출처여야 계정 전환 직후에 눌러도 서명 주소와 전송 주소가 어긋나지 않는다.
      // 연결 전이면 null 이 오므로 별도의 연결 여부 검사도 필요 없다.
      const address = await getWalletAddress()
      if (!address) throw createAppError(ERROR_KIND.WALLET_DISCONNECTED)

      // wallet_nonce.chain_id 가 NOT NULL 이라 값이 없으면 요청 자체를 보내지 않는다.
      const chainId = await getChainIdNumber()
      if (chainId === null) {
        throw createAppError(ERROR_KIND.WRONG_NETWORK, {
          message: '지갑 네트워크를 확인할 수 없습니다. 지갑을 다시 연결해 주세요.',
        })
      }

      const { nonce, message } = await requestWalletNonce({ walletAddress: address, chainId })

      // 서명 정본은 서버가 준 message 다. 우리가 아는 형식과 다르면 여기서 멈춘다 —
      // 사용자가 알 수 없는 문자열에 서명하게 두지 않고, 계약이 어긋난 사실을
      // 서명 팝업이 뜨기 전에 드러낸다.
      if (message !== buildLoginMessage(address, nonce)) {
        throw createAppError(ERROR_KIND.API_ERROR, {
          message: '서명 요청 형식이 서버와 일치하지 않습니다. 관리자에게 문의해 주세요.',
        })
      }

      // 서명 취소(4001)는 walletService 가 SIGNATURE_REJECTED 로 정규화해 던진다.
      // 여기서 따로 잡지 않고 호출부가 error.kind 로 분기하게 둔다.
      const signature = await signMessage(message)

      // nonce 를 함께 보낸다. 서버가 어느 발급분에 대한 서명인지 특정해야
      // 정확히 그 한 건만 1회용으로 소모할 수 있다.
      const { accessToken, user } = await verifyWalletSignature({
        walletAddress: address,
        nonce,
        signature,
      })

      return { accessToken, user: toAuthUser(user, address) }
    },
    onSuccess: ({ user, accessToken }) => signIn(user, accessToken),
  })
}
