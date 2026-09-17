import { MagnifyingGlass } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button, ErrorMessage, Input } from '../../../components/common/index.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import type { CompanyProfile } from '../api/getCompanyProfile.ts'
import type { CompanyProfileUpdateRequest } from '../api/updateCompanyProfile.ts'

export interface CompanyProfileFormProps {
  profile: CompanyProfile
  onSubmit: (request: CompanyProfileUpdateRequest) => void
  onCancel: () => void
  isSubmitting?: boolean
  error?: unknown
}

/**
 * 업체 정보 수정.
 *
 * <b>고칠 수 있는 것만 칸으로 만든다.</b> 업체명·사업자등록번호·이메일·업체 유형은
 * 신원과 심사에 쓰인 값이라 서버가 받지 않는다. 잠긴 칸으로 보여 주지도 않는다 —
 * 회색 입력칸은 "언젠가 고칠 수 있다" 로 읽히는데 그런 경로가 없다. 그 값들은 위쪽
 * 조회 화면에 그대로 있다.
 *
 * <b>검증은 서버 규칙을 그대로 따른다</b>(`CompanyProfileUpdateRequest`). 느슨하게
 * 두면 다 채우고 저장한 뒤에야 400 을 보고, 빡빡하게 두면 서버가 받아 줄 값을 화면이
 * 거절한다.
 *
 * <b>주소는 검색으로만 채운다.</b> 가입·배송지 화면과 같은 판단이다 — 우편번호와 주소가
 * 서로 다른 곳을 가리키는 조합은 손으로 적을 때 쉽게 만들어지고, 화면은 그것을 알 수 없다.
 */
export function CompanyProfileForm({
  profile,
  onSubmit,
  onCancel,
  isSubmitting = false,
  error,
}: CompanyProfileFormProps) {
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const {
    register,
    handleSubmit,
    setFocus,
    setValue,
    formState: { errors },
  } = useForm<CompanyProfileUpdateRequest>({
    // 지금 값으로 채워 둔다. 빈 폼을 주면 한 칸만 고치려는 사람이 나머지를 다시 적어야
    // 하고, 서버가 전부 필수로 받으므로 빠뜨리면 저장이 막힌다.
    defaultValues: {
      representativeName: profile.representativeName,
      companyPhone: profile.companyPhone,
      zipCode: profile.zipCode,
      address: profile.address,
      addressDetail: profile.addressDetail ?? '',
      introduction: profile.introduction ?? '',
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
    <form className="company-profile-form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <Input
        label="대표자명"
        autoComplete="name"
        error={errors.representativeName?.message}
        {...register('representativeName', {
          required: '대표자명을 입력해 주세요.',
          validate: (value) =>
            value.trim().length >= 2 || '대표자명은 2자 이상 입력해 주세요.',
          maxLength: { value: 100, message: '대표자명은 100자 이하로 입력해 주세요.' },
        })}
      />

      <Input
        label="업체 연락처"
        autoComplete="tel"
        placeholder="02-1234-5678"
        hint="휴대폰이 아니라 업체 대표 번호입니다."
        error={errors.companyPhone?.message}
        {...register('companyPhone', {
          required: '업체 연락처를 입력해 주세요.',
          // 서버와 같은 정규식이다. 여기만 느슨하면 저장 버튼을 누른 뒤에야 막힌다.
          pattern: {
            value: /^0\d{1,2}-?\d{3,4}-?\d{4}$/,
            message: '업체 연락처 형식이 올바르지 않습니다. (예: 02-1234-5678)',
          },
        })}
      />

      <fieldset className="company-profile-form__address">
        <legend>업체 주소</legend>

        {/*
          우편번호와 주소는 <b>읽기 전용</b>이다. 값이 없는 것이 아니라 손으로 고칠 수
          없다는 뜻이라, 비활성이 아니라 readOnly 로 둔다 — 비활성 입력은 폼 제출에서
          값이 빠지고 보조 기술도 건너뛴다.
        */}
        <div className="company-profile-form__zip">
          <Input
            label="우편번호"
            readOnly
            error={errors.zipCode?.message}
            {...register('zipCode', { required: '주소를 검색해 주세요.' })}
          />
          <Button
            type="button"
            variant="secondary"
            onClick={() => void searchAddress()}
            loading={isSearchingAddress}
          >
            <MagnifyingGlass aria-hidden="true" />
            주소 검색
          </Button>
        </div>

        <Input
          label="주소"
          readOnly
          error={errors.address?.message}
          {...register('address', { required: '주소를 검색해 주세요.' })}
        />

        {/* 상세 주소만 직접 적는다. 동·호수는 우편번호 검색이 알려 주지 않는다. */}
        <Input
          label="상세 주소"
          placeholder="동·호수 (선택)"
          error={errors.addressDetail?.message}
          {...register('addressDetail', {
            maxLength: { value: 255, message: '상세 주소는 255자 이하로 입력해 주세요.' },
          })}
        />

        {addressSearchError ? <ErrorMessage error={addressSearchError} /> : null}
      </fieldset>

      {/*
        공통 `Input` 은 한 줄 전용이라(`InputHTMLAttributes<HTMLInputElement>`) 여기서만
        같은 `ui-field` 마크업으로 여러 줄 칸을 만든다. 2000자를 한 줄에 받으면 방금 적은
        앞부분이 보이지 않아 사람이 무엇을 썼는지 확인할 수 없다.
      */}
      <div className="ui-field">
        <label className="ui-field__label" htmlFor="company-introduction">
          업체 소개
        </label>
        <textarea
          id="company-introduction"
          rows={4}
          placeholder="어떤 소재를 다루는지, 어떤 거래를 원하는지 (선택)"
          aria-invalid={errors.introduction ? true : undefined}
          aria-describedby={
            errors.introduction ? 'company-introduction-error' : 'company-introduction-hint'
          }
          className={`ui-field__control${errors.introduction ? ' ui-field__control--error' : ''}`}
          {...register('introduction', {
            maxLength: { value: 2000, message: '업체 소개는 2000자 이하로 입력해 주세요.' },
          })}
        />
        {errors.introduction ? (
          <p className="ui-field__error" id="company-introduction-error" role="alert">
            {errors.introduction.message}
          </p>
        ) : (
          <p className="ui-field__hint" id="company-introduction-hint">
            거래 상대가 이 글로 우리를 판단합니다.
          </p>
        )}
      </div>

      {error ? <ErrorMessage error={error} /> : null}

      <div className="company-profile-form__actions">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          취소
        </Button>
        <Button type="submit" loading={isSubmitting}>
          저장
        </Button>
      </div>
    </form>
  )
}
