/**
 * Backend `WalletLoginMessage.build()`와 공유하는 지갑 소유 확인 메시지다.
 * 로그인과 계정 등록 모두 같은 정본을 검산한 뒤 서버가 내려준 메시지에 서명한다.
 */
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
