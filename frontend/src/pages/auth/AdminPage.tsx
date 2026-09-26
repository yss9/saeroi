import { CompanyRoleApprovalPanel } from '../../features/admin/components/CompanyRoleApprovalPanel.tsx'
import { PredictionMarketCreatePanel } from '../../features/admin/components/PredictionMarketCreatePanel.tsx'
import { BlockchainVerificationPanel } from '../../features/admin/blockchain/BlockchainVerificationPanel.tsx'
import { WalletButton } from '../../features/wallet/components/WalletButton.tsx'
import './admin-page.css'

export type AdminSection = 'applications' | 'prediction' | 'blockchain'

export function AdminPage({ section }: { section: AdminSection }) {
  return (
    <main className="page admin-page">
      <aside className="admin-page__wallet" aria-label="관리자 지갑">
        <div>
          <strong>관리자 지갑</strong>
          <p>MetaMask에서 선택한 계정과 연결 환경을 확인합니다.</p>
        </div>
        <WalletButton />
      </aside>
      {section === 'applications' ? <CompanyRoleApprovalPanel /> : null}
      {section === 'prediction' ? <PredictionMarketCreatePanel /> : null}
      {section === 'blockchain' ? <BlockchainVerificationPanel /> : null}
    </main>
  )
}
