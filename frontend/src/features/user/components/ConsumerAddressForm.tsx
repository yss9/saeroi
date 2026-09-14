import { MagnifyingGlass } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { formatPhoneNumber, MOBILE_PHONE_PATTERN } from '../../../utils/phone.ts'
import type { ConsumerAddress, ConsumerAddressRequest } from '../types/consumerAddress.types.ts'
import './consumer-address.css'

export interface ConsumerAddressFormProps {
  /** 수정할 배송지. 없으면 새로 등록한다 */
  address?: ConsumerAddress
  onSubmit: (request: ConsumerAddressRequest) => void
  onCancel: () => void
  isSubmitting?: boolean
  error?: unknown
}

/**
 * 배송지 입력.
 *
 * **등록과 수정이 같은 폼이다.** 서버도 두 경로가 같은 본문을 받는다 — 화면을 나누면
 * 한쪽에만 검증이 붙는 일이 생긴다.
 *
 * 우편번호와 주소는 **검색으로만** 채운다. 가입 화면과 같은 판단이다 — 택배 송장에
 * 인쇄될 값이라 오타가 그대로 배송 사고가 되고, 우편번호와 주소가 서로 다른 곳을
 * 가리키는 조합은 손으로 적을 때 쉽게 만들어진다.
 *
 * ⚠️ **기본 배송지 여부를 여기서 받지 않는다.** 서버가 그 요청을 받지 않는다 — 주소를
 * 고치다가 기본이 조용히 옮겨 가면 사용자는 모른 채 다른 곳으로 물건을 받는다.
 */
export function ConsumerAddressForm({
  address,
  onSubmit,
  onCancel,
  isSubmitting = false,
  error,
}: ConsumerAddressFormProps) {
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const {
    register,
    handleSubmit,
    setFocus,
    setValue,
    formState: { errors },
  } = useForm<ConsumerAddressRequest>({
    defaultValues: {
      alias: address?.alias ?? '',
      receiverName: address?.receiverName ?? '',
      receiverPhone: address?.receiverPhone ?? '',
      zipCode: address?.zipCode ?? '',
      address: address?.address ?? '',
      // 서버가 빈 값을 저장하지 않아 null 로 온다. 폼은 빈 문자열을 다룬다.
      addressDetail: address?.addressDetail ?? '',
    },
  })

  async function searchAddress() {
    setAddressSearchError('')
    setIsSearchingAddress(true)
    try {
      await openKakaoPostcode(({ zonecode, address: found }) => {
        setValue('zipCode', zonecode, { shouldDirty: true, shouldValidate: true })
        setValue('address', found, { shouldDirty: true, shouldValidate: true })
        setFocus('addressDetail')
      })
    } catch {
      setAddressSearchError('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSearchingAddress(false)
    }
  }

  return (
    <form className="address-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <Input
        label="배송지 이름"
        placeholder="집, 회사처럼 알아보기 쉬운 이름"
        hint="주소를 고를 때 이 이름이 보입니다."
        error={errors.alias?.message}
        {...register('alias', {
          required: '배송지 이름을 입력해 주세요.',
          maxLength: { value: 100, message: '배송지 이름은 100자 이하로 입력해 주세요.' },
        })}
      />

      <div className="address-form__row">
        <Input
          label="수령인"
          autoComplete="name"
          placeholder="받는 분 이름"
          error={errors.receiverName?.message}
          {...register('receiverName', {
            required: '수령인 이름을 입력해 주세요.',
            validate: (value) => value.trim().length >= 2 || '이름은 2자 이상 입력해 주세요.',
          })}
        />
        <Input
          label="수령인 연락처"
          type="tel"
          inputMode="tel"
          maxLength={13}
          autoComplete="tel"
          placeholder="010-1234-5678"
          error={errors.receiverPhone?.message}
          {...register('receiverPhone', {
            required: '수령인 연락처를 입력해 주세요.',
            onChange: (event) => {
              event.target.value = formatPhoneNumber(event.target.value)
            },
            pattern: {
              value: MOBILE_PHONE_PATTERN,
              message: '올바른 휴대전화 번호를 입력해 주세요.',
            },
          })}
        />
      </div>

      <div className="address-form__row">
        <Input
          label="우편번호"
          inputMode="numeric"
          autoComplete="postal-code"
          placeholder="06236"
          readOnly
          error={errors.zipCode?.message}
          {...register('zipCode', { required: '주소 검색으로 우편번호를 채워 주세요.' })}
        />
        <div className="consumer-signup__address-search">
          <Input
            label="주소"
            autoComplete="street-address"
            placeholder="주소 검색을 이용해 주세요."
            readOnly
            error={errors.address?.message}
            {...register('address', {
              required: '주소를 검색해 주세요.',
              validate: (value) => value.trim().length >= 5 || '주소를 5자 이상 입력해 주세요.',
            })}
          />
          <button type="button" onClick={() => void searchAddress()} disabled={isSearchingAddress}>
            <MagnifyingGlass aria-hidden="true" />
            {isSearchingAddress ? '불러오는 중' : '주소 검색'}
          </button>
        </div>
      </div>

      {/* 두 칸이 읽기 전용이라 검색이 실패하면 손으로 메울 방법이 없다. */}
      {addressSearchError ? (
        <p className="consumer-signup__address-error" role="alert">
          {addressSearchError}
        </p>
      ) : null}

      <Input
        label="상세 주소"
        autoComplete="address-line2"
        placeholder="동·호수 등 상세 주소를 입력해 주세요. (선택)"
        error={errors.addressDetail?.message}
        {...register('addressDetail', {
          maxLength: { value: 255, message: '상세 주소는 255자 이하로 입력해 주세요.' },
        })}
      />

      {/* 저장이 실패한 이유는 폼 안에 둔다 — 목록 위에 띄우면 입력하던 자리에서 안 보인다. */}
      {error !== undefined && error !== null && <ErrorMessage error={error} />}

      <div className="address-form__actions">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          취소
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {address ? '수정' : '등록'}
        </Button>
      </div>
    </form>
  )
}
