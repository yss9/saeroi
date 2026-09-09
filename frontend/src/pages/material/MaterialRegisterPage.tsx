import { useNavigate } from 'react-router-dom'
import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { MaterialForm } from '../../features/material/components/MaterialForm.tsx'

/** SUPPLIER 전용. Role 제한은 라우터의 RoleGuard 가 건다. */
export function MaterialRegisterPage() {
  const navigate = useNavigate()

  return (
    <CompanyDashboardLayout>
      <div className="page">
        <h1>폐소재 등록</h1>
        {/* 등록 후 어디로 갈지는 Page 가 정한다 — Feature 는 화면 이동을 알지 못한다. */}
        <MaterialForm onCreated={(materialId) => navigate(`/materials/${materialId}`)} />
      </div>
    </CompanyDashboardLayout>
  )
}
