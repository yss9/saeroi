import { useState } from 'react'
import { CompanyRoleApprovalPanel } from '../../features/admin/components/CompanyRoleApprovalPanel.tsx'
import { PredictionMarketCreatePanel } from '../../features/admin/components/PredictionMarketCreatePanel.tsx'
import { BlockchainVerificationPanel } from '../../features/admin/blockchain/BlockchainVerificationPanel.tsx'
import './admin-page.css'

export function AdminPage() {
  const [section, setSection] = useState<'applications' | 'prediction' | 'blockchain'>('applications')

  return (
    <main className="page">
      <nav className={`admin-page__tabs ${section === 'blockchain' ? 'is-blockchain' : ''}`} aria-label="관리자 기능">
        <button type="button" className={section === 'applications' ? 'is-active' : ''}
          onClick={() => setSection('applications')}>사업자 가입 승인</button>
        <button type="button" className={section === 'prediction' ? 'is-active' : ''}
          onClick={() => setSection('prediction')}>예측시장 개설</button>
        <button type="button" className={section === 'blockchain' ? 'is-active' : ''}
          onClick={() => setSection('blockchain')}>블록체인 검증</button>
      </nav>
      {section === 'applications' ? <CompanyRoleApprovalPanel /> : null}
      {section === 'prediction' ? <PredictionMarketCreatePanel /> : null}
      {section === 'blockchain' ? <BlockchainVerificationPanel /> : null}
    </main>
  )
}
