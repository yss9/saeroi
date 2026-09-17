import { CheckCircle, Clock, Wallet, XCircle } from '@phosphor-icons/react'
import { ErrorMessage } from '../../../components/common/index.ts'
import { ROLE_LABEL } from '../../../types/role.types.ts'
import { shortenAddress } from '../../../utils/address.ts'
import { useApproveCompanyRole, useCompanyRoleApplications, useRejectCompanyRole } from '../hooks/useCompanyRoleApplications.ts'
import type { CompanyRoleApprovalStatus } from '../types/companyRoleApproval.types.ts'
import './company-role-approval.css'

const STATUS_LABEL: Record<CompanyRoleApprovalStatus, string> = {
  WAITING_WALLET: '지갑 등록 대기', PENDING: '승인 대기', APPROVED: '승인 완료', REJECTED: '반려',
}

export function CompanyRoleApprovalPanel() {
  const applications = useCompanyRoleApplications()
  const approve = useApproveCompanyRole()
  const reject = useRejectCompanyRole()
  const pendingUserId = approve.variables ?? reject.variables ?? null

  if (applications.isLoading) return <p className="admin-role__state">가입 승인 목록을 불러오는 중입니다.</p>
  if (applications.error) return <ErrorMessage error={applications.error} />

  return (
    <section className="admin-role" aria-labelledby="company-role-title">
      <header className="admin-role__header">
        <div><p className="admin-role__eyebrow">BLOCKCHAIN ACCESS</p><h1 id="company-role-title">사업자 가입 승인</h1><p>검증된 지갑을 확인한 뒤 회원 유형에 맞는 온체인 역할을 자동으로 부여합니다.</p></div>
        <span className="admin-role__count">{applications.data?.length ?? 0}개 업체</span>
      </header>

      {applications.data?.length ? <div className="admin-role__list">
        {applications.data.map((application) => {
          const working = pendingUserId === application.userId && (approve.isPending || reject.isPending)
          const canApprove = application.walletVerified && application.status !== 'APPROVED'
          return <article className="admin-role__card" key={application.userId}>
            <div className="admin-role__summary"><div>
              <span className={`admin-role__status admin-role__status--${application.status.toLowerCase()}`}>
                {application.status === 'APPROVED' ? <CheckCircle weight="fill" /> : application.status === 'REJECTED' ? <XCircle weight="fill" /> : <Clock weight="fill" />}{STATUS_LABEL[application.status]}
              </span>
              <h2>{application.companyName}</h2><p>{ROLE_LABEL[application.role]} · {application.businessNumber}</p>
            </div><dl><div><dt>대표자</dt><dd>{application.representativeName}</dd></div><div><dt>이메일</dt><dd>{application.email}</dd></div></dl></div>
            <div className="admin-role__wallet"><Wallet aria-hidden="true" /><div><span>검증 지갑</span><code title={application.walletAddress ?? undefined}>{application.walletAddress ? shortenAddress(application.walletAddress) : '등록되지 않음'}</code></div></div>
            {application.transactionHashes.length > 0 ? <p className="admin-role__transactions">온체인 반영 {application.transactionHashes.length}건 완료</p> : null}
            <div className="admin-role__actions">
              <button type="button" className="admin-role__reject" disabled={working || application.status === 'APPROVED'} onClick={() => reject.mutate(application.userId)}>반려</button>
              <button type="button" className="admin-role__approve" disabled={working || !canApprove} onClick={() => approve.mutate(application.userId)}>{working ? '처리 중…' : application.status === 'APPROVED' ? '승인 완료' : '승인 및 역할 부여'}</button>
            </div>
          </article>
        })}
      </div> : <p className="admin-role__state">심사할 사업자 계정이 없습니다.</p>}
      {approve.error ? <ErrorMessage error={approve.error} /> : null}{reject.error ? <ErrorMessage error={reject.error} /> : null}
    </section>
  )
}
