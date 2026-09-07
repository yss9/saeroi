import { useMutation } from '@tanstack/react-query'
import { useAuthStore } from '../../../stores/authStore.ts'
import { createAppError, ERROR_KIND, type AppError } from '../../../utils/errors.ts'
import {
  registerWallet,
  requestWalletRegistrationNonce,
  type RegisteredWallet,
} from '../api/walletRegistrationApi.ts'
import { buildLoginMessage } from '../services/walletMessage.ts'
import { getChainIdNumber, getWalletAddress, signMessage } from '../services/walletService.ts'

export function useWalletRegistration() {
  const patchUser = useAuthStore((state) => state.patchUser)

  return useMutation<RegisteredWallet, AppError>({
    mutationFn: async () => {
      const walletAddress = await getWalletAddress()
      if (!walletAddress) throw createAppError(ERROR_KIND.WALLET_DISCONNECTED)

      const chainId = await getChainIdNumber()
      if (chainId === null) {
        throw createAppError(ERROR_KIND.WRONG_NETWORK, {
          message: '지갑 네트워크를 확인할 수 없습니다. 지갑을 다시 연결해 주세요.',
        })
      }

      const challenge = await requestWalletRegistrationNonce({ walletAddress, chainId })
      const expectedMessage = buildLoginMessage(challenge.walletAddress, challenge.nonce)

      if (
        challenge.walletAddress.toLowerCase() !== walletAddress.toLowerCase() ||
        challenge.message !== expectedMessage
      ) {
        throw createAppError(ERROR_KIND.API_ERROR, {
          message: '서명 요청 형식이 서버와 일치하지 않습니다. 관리자에게 문의해 주세요.',
        })
      }

      const signature = await signMessage(challenge.message)
      return registerWallet({
        walletAddress: challenge.walletAddress,
        chainId,
        nonce: challenge.nonce,
        signature,
      })
    },
    onSuccess: (wallet) => patchUser({ walletAddress: wallet.walletAddress }),
  })
}
