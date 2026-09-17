import { useNavigate } from 'react-router-dom'
import { MaterialForm } from '../../features/material/components/MaterialForm.tsx'
import '../../components/common/console.css'

/** SUPPLIER 전용. Role 제한은 라우터의 RoleGuard 가 건다. */
export function MaterialRegisterPage() {
  const navigate = useNavigate()

  return (
    <main className="console">
      <header className="console__head">
        <div>
          {/*
            <b>제목은 눈에 보이지 않는다.</b> 사이드바가 이미 "소재 등록" 을 켠 채로
            띄우고 있어, 큰 제목을 또 그리면 같은 말이 두 번이다(7511d10 이 앱 전체에
            적용한 규칙). DOM 에는 남겨 둔다 — 사이드바가 접히는 좁은 화면과 보조 기술
            에게는 이것이 유일한 화면 이름이다.
          */}
          <h1 className="ui-visually-hidden">소재 등록</h1>
          <p className="console__sub">
            수거한 폐섬유를 등록하면 고유 소재 코드가 발급되고 이력 기록이 시작됩니다.
          </p>
        </div>
      </header>

      {/* 등록 후 어디로 갈지는 Page 가 정한다 — Feature 는 화면 이동을 알지 못한다.
          `replace` 를 쓰는 이유: 이 폼은 이미 제출됐다. 남겨 두면 보유 소재에서 뒤로 가기를
          눌렀을 때 채워진 폼으로 돌아와 같은 소재를 한 번 더 등록하게 된다. */}
      <MaterialForm onCreated={() => navigate('/inventory', { replace: true })} />
    </main>
  )
}
