import { CompanyRoleApprovalPanel } from '../../features/admin/components/CompanyRoleApprovalPanel.tsx'
import { PredictionMarketCreatePanel } from '../../features/admin/components/PredictionMarketCreatePanel.tsx'
import { BlockchainVerificationPanel } from '../../features/admin/blockchain/BlockchainVerificationPanel.tsx'
import './admin-page.css'

export type AdminSection = 'applications' | 'prediction' | 'blockchain'

export function AdminPage({ section }: { section: AdminSection }) {
  return (
    <main className="page admin-page">
      {section === 'applications' ? <CompanyRoleApprovalPanel /> : null}
      {section === 'prediction' ? <PredictionMarketCreatePanel /> : null}
      {section === 'blockchain' ? <BlockchainVerificationPanel /> : null}
    </main>
  )
}
