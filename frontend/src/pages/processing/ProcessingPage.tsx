import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { ProcessorRequestList } from '../../features/processing/components/ProcessorRequestList.tsx'

export function ProcessingPage() {
  return <CompanyDashboardLayout><ProcessorRequestList /></CompanyDashboardLayout>
}
