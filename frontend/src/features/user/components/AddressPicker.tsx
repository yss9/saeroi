import { Link } from 'react-router-dom'
import { useEffect } from 'react'
import { useConsumerAddresses } from '../hooks/useConsumerAddresses.ts'
import './address-picker.css'

export interface AddressPickerProps {
  /** 아직 아무것도 고르지 않았으면 null. 목록이 오면 기본 배송지로 자동 채워진다 */
  selected: number | null
  onSelect: (addressId: number) => void
  disabled?: boolean
  /** 라디오 그룹 이름. 한 화면에 여러 개 두면 서로 다른 값을 줘야 한다 */
  name?: string
}

/**
 * 배송지 라디오 목록.
 *
 * **화면을 옮기지 않는다.** 예전에는 "배송지 변경"이 `/mypage/addresses`로 이동시켜
 * 구매 흐름이 끊겼다 — 카트 결제(`CartCheckout`)가 쓰던 인라인 방식을 상품 구매에도
 * 재사용할 수 있게 공용으로 뺐다.
 *
 * **선택값을 스스로 채운다.** `selected`가 아직 `null`이면(처음 열렸을 때) 목록이 오는 대로
 * 기본 배송지(없으면 첫 번째)를 골라 `onSelect`로 올려보낸다 — 그래야 사용자가 라디오를
 * 한 번도 건드리지 않아도 "지금 선택된 배송지"가 존재한다.
 */
export function AddressPicker({
  selected,
  onSelect,
  disabled = false,
  name = 'address-picker',
}: AddressPickerProps) {
  const addresses = useConsumerAddresses()
  const fallback = addresses.data?.find((address) => address.isDefault)?.addressId
    ?? addresses.data?.[0]?.addressId
  const current = selected ?? fallback

  useEffect(() => {
    if (selected === null && fallback !== undefined) onSelect(fallback)
  }, [selected, fallback, onSelect])

  if (addresses.isPending) {
    return <p role="status">배송지를 불러오는 중</p>
  }

  if (addresses.isError) {
    return (
      <div role="alert" className="address-picker__error">
        <p>배송지를 불러오지 못했습니다.</p>
        <button type="button" onClick={() => void addresses.refetch()}>
          다시 시도
        </button>
      </div>
    )
  }

  if (!addresses.data || addresses.data.length === 0) {
    return (
      <p className="address-picker__empty">
        등록된 배송지가 없습니다.{' '}
        <Link to="/mypage/addresses">배송지를 먼저 등록해 주세요.</Link>
      </p>
    )
  }

  return (
    <div className="address-picker">
      <fieldset disabled={disabled}>
        <legend>배송지 선택</legend>
        {addresses.data.map((address) => (
          <label key={address.addressId} className="address-picker__option">
            <input
              type="radio"
              name={name}
              checked={address.addressId === current}
              onChange={() => onSelect(address.addressId)}
            />
            <span className="address-picker__address">
              <strong>{address.alias}</strong>
              <span>
                {address.receiverName} · {address.receiverPhone}
              </span>
              <span>
                [{address.zipCode}] {address.address}
                {address.addressDetail ? ` ${address.addressDetail}` : ''}
              </span>
            </span>
          </label>
        ))}
      </fieldset>
      <Link className="address-picker__manage" to="/mypage/addresses">
        배송지 등록·관리
      </Link>
    </div>
  )
}
