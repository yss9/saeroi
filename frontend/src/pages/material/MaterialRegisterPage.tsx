import { useNavigate } from 'react-router-dom'
import { MaterialForm } from '../../features/material/components/MaterialForm.tsx'

/** SUPPLIER 전용. Role 제한은 라우터의 RoleGuard 가 건다. */
export function MaterialRegisterPage() {
  const navigate = useNavigate()

  return (
    <main className="page">
      <h1>폐소재 등록</h1>
      {/* 등록 후 어디로 갈지는 Page 가 정한다 — Feature 는 화면 이동을 알지 못한다.
          `replace` 를 쓰는 이유: 이 폼은 이미 제출됐다. 남겨 두면 보유 소재에서 뒤로 가기를
          눌렀을 때 채워진 폼으로 돌아와 같은 소재를 한 번 더 등록하게 된다. */}
      <MaterialForm onCreated={() => navigate('/inventory', { replace: true })} />
    </main>
  )
}
