// "지갑으로 로그인" 버튼.
//
// 연결 상태는 useWallet() 이 아니라 useWalletStore 에서 읽는다. useWallet 을 여기서
// 한 번 더 마운트하면 walletClient 인스턴스가 하나 더 생기고 restore() 가 중복 호출된다.
//
// ⚠️ 전제: 같은 화면 어딘가에서 <WalletButton /> 이 마운트되어 스토어를 채운다
// (useWallet → walletStore 단방향 전파). LoginPage 에서는 바로 옆에 있다.
// 그걸 걷어내면 이 버튼이 영영 나타나지 않는다.

import { Button, ErrorMessage } from '../../../components/common/index.ts'
import { useWalletStore } from '../../../stores/walletStore.ts'
import { ERROR_KIND, type AppError } from '../../../utils/errors.ts'
import { useWalletLogin } from '../hooks/useWalletLogin.ts'

/**
 * 지갑 로그인 맥락에서만 다르게 말해야 하는 Backend ErrorCode.
 *
 * 여기 없는 코드는 서버가 준 문구를 그대로 쓴다. kind 별 표를 만들지 않는 것이 중요하다 —
 * 예컨대 WALLET_DISCONNECTED 를 통째로 덮어쓰면, -32002 에서 toWalletError 가 붙여 준
 * "MetaMask 창에서 이전 요청을 먼저 처리해 주세요" 같은 더 구체적인 안내가 사라진다.
 */
const LOGIN_ERROR_TEXT: Record<string, string> = {
  WALLET_NOT_LINKED:
    '이 지갑에 연결된 계정이 없습니다. 이메일로 로그인한 뒤 지갑을 연결해 주세요.',
  // 지갑은 공급업체·브랜드·가공업체만 갖는다. 소비자는 이메일 로그인을 쓴다.
  WALLET_ROLE_NOT_ALLOWED:
    '지갑 로그인은 공급업체·브랜드·가공업체 계정만 사용할 수 있습니다. 이메일로 로그인해 주세요.',
  NONCE_EXPIRED: '인증 유효 시간이 지났습니다. 다시 시도해 주세요.',
  NONCE_ALREADY_USED: '이미 사용된 인증입니다. 다시 시도해 주세요.',
  SIGNATURE_MISMATCH: '지갑 확인 결과가 계정과 일치하지 않습니다. 연결된 계정을 확인해 주세요.',
}

function loginErrorText(error: AppError): string {
  return (error.code && LOGIN_ERROR_TEXT[error.code]) || error.message
}

export interface WalletLoginButtonProps {
  /** 로그인 성공 후 이동은 호출부(Page)가 정한다 — useLogin 과 같은 규칙. */
  onSuccess?: () => void
}

export function WalletLoginButton({ onSuccess }: WalletLoginButtonProps) {
  const isConnected = useWalletStore((state) => state.isConnected)
  const { mutate, isPending, error, reset } = useWalletLogin()

  // 연결 전에는 비활성 버튼 대신 안내만 보여준다. 다음에 무엇을 해야 하는지가 분명하다.
  if (!isConnected) {
    return <p className="wallet__note">지갑을 연결하면 간편하게 로그인할 수 있습니다.</p>
  }

  const handleClick = () => {
    // 이전 실패 문구를 지우고 시작한다. 남겨 두면 재시도 중에도 옛 에러가 붙어 있다.
    reset()
    mutate(undefined, { onSuccess: () => onSuccess?.() })
  }

  return (
    <div className="wallet">
      <Button onClick={handleClick} loading={isPending} fullWidth>
        {isPending ? '지갑에서 요청 확인 중…' : '지갑으로 로그인'}
      </Button>

      {error &&
        // 서명 취소는 오류가 아니라 사용자의 정상적인 선택이다. 경고음 나는
        // role="alert" 대신 조용한 안내로 보여준다.
        (error.kind === ERROR_KIND.SIGNATURE_REJECTED ? (
          <p className="wallet__note">{error.message}</p>
        ) : (
          // ErrorMessage 는 내부에서 toErrorMessage() 를 쓰는데 그 함수는 plain string 을
          // 처리하지 못한다. 그래서 문자열이 아니라 message 만 갈아 끼운 AppError 를 넘긴다.
          <ErrorMessage error={{ ...error, message: loginErrorText(error) }} />
        ))}
    </div>
  )
}
