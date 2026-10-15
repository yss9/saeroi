import { ArrowLeft } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import './back-link.css'

export interface BackLinkProps {
  /**
   * 돌아갈 곳. <b>브라우저 히스토리가 아니라 이 값이 기준이다.</b>
   *
   * 같은 화면이 여러 경로로 열리기 때문이다 — 소재 상세는 보유 소재 목록에서도,
   * 마켓플레이스에서도, 등록 직후 리다이렉트로도 열린다. {@code navigate(-1)} 은
   * 그 중 마지막 것에서 등록 폼으로 되돌아가 방금 만든 소재를 다시 만들게 한다.
   */
  to: string
  /** 돌아갈 곳의 이름. "뒤로" 가 아니라 어디로 가는지를 쓴다. */
  label: string
}

/**
 * 화면 좌상단의 "어디로 돌아가는가".
 *
 * <b>왜 필요한가</b> — 소재 상세·판매 글 상세는 Public 라우트라
 * {@code ProtectedRoute} 의 사이드바 셸이 씌워지지 않는다. 그래서 로그인한 사용자가
 * 들어가면 메뉴가 사라지고, 브라우저 뒤로 가기 말고는 나갈 길이 없다.
 * 공개로 두는 것은 의도된 결정이므로(비회원 조회·QR), 셸 대신 이 링크로 출구를 만든다.
 *
 * <b>언제나 평범한 링크다.</b> 한때 "직전 화면이 목적지면 pop 으로 돌아가 목록의 스크롤을
 * 살린다" 는 분기를 두었는데, 그 판단에 필요한 값을 심는 곳이 아무 데도 없어 항상 거짓인
 * 죽은 코드였다. 목록 상태 보존이 필요해지면 그때 필요한 곳에서 값을 심고 되살린다 —
 * 쓰이지 않는 분기를 남겨 두면 읽는 사람이 동작을 잘못 예측한다.
 */
export function BackLink({ to, label }: BackLinkProps) {
  return (
    <Link className="back-link" to={to}>
      <ArrowLeft aria-hidden="true" />
      {label}
    </Link>
  )
}
