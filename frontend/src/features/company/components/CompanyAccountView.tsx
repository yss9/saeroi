import { Buildings, CheckCircle, Envelope, MapPin, Phone, Wallet } from '@phosphor-icons/react'
import { Button, ErrorMessage, StatusBadge } from '../../../components/common/index.ts'
import { shortenAddress } from '../../../utils/address.ts'
import { WalletRegistrationPanel } from '../../wallet/components/WalletRegistrationPanel.tsx'
import {
  BUSINESS_ROLE_LABEL,
  type CompanyAccountData,
} from '../types/companyAccount.types.ts'

export type CompanyAccountViewState =
  | { status: 'error'; error: unknown }
  | { status: 'ready'; data: CompanyAccountData }

interface CompanyAccountViewProps {
  state: CompanyAccountViewState
  walletState: CompanyWalletViewState
  onRetryWallet?: () => void
  /**
   * 수정 화면으로 보내는 손잡이.
   *
   * <b>없으면 버튼을 그리지 않는다.</b> 업체 정보를 아직 못 읽었거나(조회 중·실패)
   * 고칠 수 없는 상태면 부르는 쪽이 넘기지 않는다 — 여기서 상태를 다시 따지면
   * 같은 판단이 두 곳에 생긴다.
   */
  onEdit?: () => void
}

export type CompanyWalletViewState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; registeredAddress: string | null }

function displayValue(value: string | null) {
  return value || '등록된 정보가 없습니다.'
}

function DetailItem({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="company-account__detail">
      <dt>{label}</dt>
      <dd className={value ? undefined : 'company-account__empty-value'}>{displayValue(value)}</dd>
    </div>
  )
}

export function CompanyAccountView({ state, walletState, onRetryWallet, onEdit }: CompanyAccountViewProps) {
  if (state.status === 'error') {
    return (
      <section className="company-account" aria-labelledby="company-account-title">
        <h1 id="company-account-title" className="ui-visually-hidden">회사 정보</h1>
        <ErrorMessage error={state.error} />
      </section>
    )
  }

  const { data } = state
  const registeredAddress = walletState.status === 'ready' ? walletState.registeredAddress : null
  const walletBadge = walletState.status === 'loading'
    ? { label: '확인 중', tone: 'neutral' as const }
    : walletState.status === 'error'
      ? { label: '확인 실패', tone: 'neutral' as const }
      : registeredAddress
        ? { label: '연결됨', tone: 'success' as const }
        : { label: '미연결', tone: 'neutral' as const }

  return (
    <section className="company-account" aria-labelledby="company-account-title">
      <div className="company-account__page-heading">
        <h1 id="company-account-title" className="ui-visually-hidden">회사 정보</h1>
        <StatusBadge tone={data.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {data.status === 'ACTIVE' ? '활성 계정' : '상태 확인 필요'}
        </StatusBadge>
        {/* 고칠 수 있는 것은 대표자명·연락처·주소·소개뿐이라 버튼도 한 개다. 카드마다
            달면 "업체명도 고칠 수 있다" 로 읽힌다 — 그건 서버가 받지 않는다. */}
        {onEdit ? (
          <Button variant="secondary" size="sm" onClick={onEdit}>
            정보 수정
          </Button>
        ) : null}
      </div>

      <article className="company-account__summary">
        <span className="company-account__company-icon" aria-hidden="true"><Buildings /></span>
        <div>
          <div className="company-account__summary-title">
            <h2>{data.companyName}</h2>
            <StatusBadge tone="info">{BUSINESS_ROLE_LABEL[data.role]}</StatusBadge>
          </div>
          <p>{data.email}</p>
        </div>
      </article>

      <div className="company-account__grid">
        <article className="company-account__card">
          <div className="company-account__card-heading">
            <Buildings aria-hidden="true" />
            <div>
              <h2>사업자 정보</h2>
            </div>
          </div>
          <dl>
            <DetailItem label="업체명" value={data.companyName} />
            <DetailItem label="업체 유형" value={BUSINESS_ROLE_LABEL[data.role]} />
            <DetailItem label="사업자등록번호" value={data.businessNumber} />
            <DetailItem label="대표자명" value={data.representativeName} />
          </dl>
        </article>

        <article className="company-account__card">
          <div className="company-account__card-heading">
            <Envelope aria-hidden="true" />
            <div>
              <h2>연락처 정보</h2>
            </div>
          </div>
          <dl>
            <DetailItem label="이메일" value={data.email} />
            <DetailItem label="업체 연락처" value={data.companyPhone} />
            <DetailItem label="업체 주소" value={data.companyAddress} />
          </dl>
          <div className="company-account__contact-icons" aria-hidden="true">
            <Phone /><MapPin />
          </div>
        </article>
      </div>

      <article id="company-wallet" className="company-account__wallet-card">
        <div className="company-account__wallet-heading">
          <span className="company-account__wallet-icon" aria-hidden="true"><Wallet /></span>
          <div>
            <h2>업무용 지갑</h2>
          </div>
          <StatusBadge tone={walletBadge.tone}>{walletBadge.label}</StatusBadge>
        </div>

        <div className="company-account__wallet-content">
          {walletState.status === 'loading' ? (
            <p className="company-account__wallet-loading" aria-live="polite">등록된 지갑 정보를 확인하고 있습니다.</p>
          ) : walletState.status === 'error' ? (
            <div className="company-account__wallet-error">
              <ErrorMessage error={walletState.error} />
              {onRetryWallet ? <Button variant="secondary" size="sm" onClick={onRetryWallet}>다시 확인</Button> : null}
            </div>
          ) : (
            <>
              {registeredAddress ? (
                <div className="company-account__registered-wallet">
                  <CheckCircle weight="fill" aria-hidden="true" />
                  <div>
                    <span>등록된 지갑 주소</span>
                    <code title={registeredAddress}>{shortenAddress(registeredAddress)}</code>
                  </div>
                </div>
              ) : (
                <div className="company-account__wallet-empty">
                  <strong>아직 연결된 지갑이 없습니다.</strong>
                  <span>업무에 사용할 지갑을 연결해 주세요.</span>
                </div>
              )}
              <WalletRegistrationPanel registeredAddress={registeredAddress} />
            </>
          )}
        </div>
      </article>
    </section>
  )
}
