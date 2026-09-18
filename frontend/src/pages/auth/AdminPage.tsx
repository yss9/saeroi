import { useState } from 'react'
import { CompanyRoleApprovalPanel } from '../../features/admin/components/CompanyRoleApprovalPanel.tsx'
import { PredictionMarketCreatePanel } from '../../features/admin/components/PredictionMarketCreatePanel.tsx'
import './admin-page.css'

export function AdminPage() {
  const [section, setSection] = useState<'applications' | 'prediction'>('applications')

  return (
    <main className="page">
      <nav className="admin-page__tabs" aria-label="관리자 기능">
        <button type="button" className={section === 'applications' ? 'is-active' : ''}
          onClick={() => setSection('applications')}>사업자 가입 승인</button>
        <button type="button" className={section === 'prediction' ? 'is-active' : ''}
          onClick={() => setSection('prediction')}>예측시장 개설</button>
      </nav>
      {section === 'applications' ? <CompanyRoleApprovalPanel /> : <PredictionMarketCreatePanel />}
    </main>
  )
}
