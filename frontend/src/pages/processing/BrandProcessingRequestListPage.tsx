import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { BrandProcessingRequestList } from '../../features/processing/components/BrandProcessingRequestList.tsx'

export function BrandProcessingRequestListPage() {
  return (
    <CompanyDashboardLayout>
      <BrandProcessingRequestList />
    </CompanyDashboardLayout>
  )
}
