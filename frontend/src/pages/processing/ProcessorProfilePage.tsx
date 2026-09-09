// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { ProcessorProfileForm } from '../../features/processing/components/ProcessorProfileForm.tsx'

export function ProcessorProfilePage() {
  return <ProcessorProfileForm />
}
