import { useState } from 'react'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
} from '../../../components/common/index.ts'
import { useConsumerAddressActions } from '../hooks/useConsumerAddressActions.ts'
import { useConsumerAddresses } from '../hooks/useConsumerAddresses.ts'
import {
  CONSUMER_ADDRESS_LIMIT,
  type ConsumerAddress,
} from '../types/consumerAddress.types.ts'
import { ConsumerAddressForm } from './ConsumerAddressForm.tsx'
import './consumer-address.css'

/** 지금 화면이 무엇을 하고 있는가. 목록·등록이 같은 자리를 쓴다. */
type Mode = { kind: 'list' } | { kind: 'add' }

/**
 * 배송지 주소록.
 *
 * **가입할 때 입력한 주소가 첫 배송지로 들어와 있다.** 그래서 이 화면의 첫 방문은 보통
 * 빈 목록이 아니라 한 건짜리 목록이다 — 비어 있는 경우는 주소를 받기 전에 가입한 계정뿐이고,
 * 그것도 에러가 아니라 정상 상태다 (ARCHITECTURE §8).
 */
export function ConsumerAddressBook() {
  const { data, isPending, isError, error, refetch } = useConsumerAddresses()
  const actions = useConsumerAddressActions()
  const [mode, setMode] = useState<Mode>({ kind: 'list' })

  // 삭제는 되돌릴 수 없다. 확인을 거칠 대상을 들고 있는다.
  const [removing, setRemoving] = useState<ConsumerAddress | null>(null)

  if (isPending) return <Loading variant="skeleton" rows={3} label="배송지를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  if (mode.kind === 'add') {
    return (
      <section className="address-book">
        <h2 className="address-book__title">새 배송지</h2>
        <ConsumerAddressForm
          isSubmitting={actions.add.isPending}
          error={actions.add.error}
          onCancel={() => {
            actions.add.reset()
            setMode({ kind: 'list' })
          }}
          onSubmit={(request) => {
            actions.add.mutate(request, { onSuccess: () => setMode({ kind: 'list' }) })
          }}
        />
      </section>
    )
  }

  // 서버가 20건에서 거절한다. 눌러 보고 알게 하지 않고 미리 적는다.
  const isFull = data.length >= CONSUMER_ADDRESS_LIMIT

  return (
    <section className="address-book">
      <div className="address-book__header">
        <h2 className="address-book__title">배송지 {data.length}개</h2>
        {isFull ? (
          <p className="address-book__limit">
            배송지는 {CONSUMER_ADDRESS_LIMIT}개까지 등록할 수 있습니다. 새로 추가하려면 쓰지
            않는 배송지를 먼저 지워 주세요.
          </p>
        ) : (
          <Button size="sm" onClick={() => setMode({ kind: 'add' })}>
            새 배송지 추가
          </Button>
        )}
      </div>

      {/* 목록에서 일어난 실패(기본 지정·삭제)는 목록 위에 둔다. 어느 줄에서 났는지는
          버튼이 원래 자리에 남아 있어 사용자가 안다. */}
      {actions.setDefault.isError && <ErrorMessage error={actions.setDefault.error} />}
      {actions.remove.isError && <ErrorMessage error={actions.remove.error} />}

      {data.length === 0 ? (
        <EmptyState
          title="등록된 배송지가 없습니다"
          description="배송지를 등록하면 주문할 때 주소를 다시 입력하지 않아도 됩니다."
          action={<Button onClick={() => setMode({ kind: 'add' })}>첫 배송지 등록하기</Button>}
        />
      ) : (
        <ul className="address-book__list">
          {data.map((item) => (
            <li key={item.addressId} className="address-book__item">
              <AddressCard
                address={item}
                busy={
                  (actions.setDefault.isPending && actions.setDefault.variables === item.addressId) ||
                  (actions.remove.isPending && actions.remove.variables === item.addressId)
                }
                onRemove={() => setRemoving(item)}
                onSetDefault={() => actions.setDefault.mutate(item.addressId)}
              />
            </li>
          ))}
        </ul>
      )}

      {/* 삭제는 되돌릴 수 없다. 이름을 문장에 넣어 어느 배송지인지 확인시킨다. */}
      <ConfirmModal
        open={removing !== null}
        title="배송지를 지울까요?"
        message={`${removing?.alias ?? ''}(${removing?.receiverName ?? ''})을(를) 지웁니다. 되돌릴 수 없습니다.`}
        confirmLabel="삭제"
        destructive
        loading={actions.remove.isPending}
        onConfirm={() => {
          if (removing === null) return
          actions.remove.mutate(removing.addressId, { onSettled: () => setRemoving(null) })
        }}
        onClose={() => setRemoving(null)}
      />
    </section>
  )
}

/**
 * 배송지 한 건.
 *
 * **기본 배송지에는 "기본으로 지정" 을 두지 않는다.** 이미 기본인 것을 다시 지정하는
 * 버튼은 눌러도 아무 일이 없고, 눌러도 되는 것처럼 보이면 사용자는 자기 화면이 멈췄다고
 * 읽는다.
 */
function AddressCard({
  address,
  busy,
  onRemove,
  onSetDefault,
}: {
  address: ConsumerAddress
  busy: boolean
  onRemove: () => void
  onSetDefault: () => void
}) {
  return (
    <article className="address-card">
      <div className="address-card__head">
        <strong className="address-card__alias">{address.alias}</strong>
        {address.isDefault && <StatusBadge tone="success">기본 배송지</StatusBadge>}
      </div>

      <p className="address-card__receiver">
        {address.receiverName} · {address.receiverPhone}
      </p>

      <address className="address-card__address">
        <span>
          [{address.zipCode}] {address.address}
        </span>
        {address.addressDetail && <span>{address.addressDetail}</span>}
      </address>

      <div className="address-card__actions">
        {!address.isDefault && (
          <Button variant="secondary" size="sm" onClick={onSetDefault} disabled={busy}>
            기본으로 지정
          </Button>
        )}
        <Button variant="secondary" size="sm" onClick={onRemove} disabled={busy}>
          삭제
        </Button>
      </div>
    </article>
  )
}
