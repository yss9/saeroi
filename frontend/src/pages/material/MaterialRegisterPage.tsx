import { useNavigate } from 'react-router-dom'
import { MaterialForm } from '../../features/material/components/MaterialForm.tsx'

/**
 * SUPPLIER 전용. Role 제한은 라우터의 RoleGuard 가 건다.
 *
 * <b>소재 검색과 같은 골격(`.page`)을 입는다.</b> 예전에는 공급업체 콘솔(`console.css`)의
 * 초록·고정폭 디자인이었는데, 같은 "소재" 묶음의 소재 검색 화면과 나란히 오가면 두 화면이
 * 다른 서비스처럼 보였다. 등록한 소재가 곧 검색에 올라가는 판매 글이 되므로, 올리는
 * 화면과 보이는 화면이 한 모양이어야 한다.
 */
export function MaterialRegisterPage() {
  const navigate = useNavigate()

  return (
    <main className="page">
      {/*
        <b>제목은 눈에 보이지 않는다.</b> 사이드바가 이미 "소재 등록" 을 켠 채로
        띄우고 있어, 큰 제목을 또 그리면 같은 말이 두 번이다(7511d10 이 앱 전체에
        적용한 규칙). DOM 에는 남겨 둔다 — 사이드바가 접히는 좁은 화면과 보조 기술
        에게는 이것이 유일한 화면 이름이다.
      */}
      <h1 className="ui-visually-hidden">소재 등록</h1>

      {/* 등록 후 어디로 갈지는 Page 가 정한다 — Feature 는 화면 이동을 알지 못한다.
          `replace` 를 쓰는 이유: 이 폼은 이미 제출됐다. 남겨 두면 보유 소재에서 뒤로 가기를
          눌렀을 때 채워진 폼으로 돌아와 같은 소재를 한 번 더 등록하게 된다. */}
      <MaterialForm onCreated={() => navigate('/inventory', { replace: true })} />
    </main>
  )
}
