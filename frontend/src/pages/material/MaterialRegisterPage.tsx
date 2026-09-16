import { useNavigate } from 'react-router-dom'
import { MaterialForm } from '../../features/material/components/MaterialForm.tsx'
import '../../features/supplier/components/supplier-console.css'

/** SUPPLIER 전용. Role 제한은 라우터의 RoleGuard 가 건다. */
export function MaterialRegisterPage() {
  const navigate = useNavigate()

  return (
    <main className="sup">
      <header className="sup__head">
        <div>
          <div className="sup__title">
            {/* 사이드바 메뉴는 "소재 등록" 이다. 같은 이름을 쓴다. */}
            <h1>소재 등록</h1>
            <span className="sup__tag">material-new</span>
          </div>
          <p className="sup__sub">
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
