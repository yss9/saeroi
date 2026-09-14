import { Eye, EyeSlash, Factory, MagnifyingGlass, PaintBrush, Package } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Button, Input } from '../../../components/common/index.ts'
import type {
  BusinessRole,
  BusinessSignupFormValues,
  BusinessSignupSubmitValues,
} from '../types/signup.types.ts'
import { formatPhoneNumber } from '../../../utils/phone.ts'
import { formatBusinessNumber } from '../utils/signupInputFormatters.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'

interface BusinessSignupFormProps {
  onSubmit: (values: BusinessSignupSubmitValues) => void | Promise<void>
  isSubmitting?: boolean
}
const businessRoles = [
  {
    value: 'SUPPLIER',
    label: '공급업체',
    description: '순환 소재를 등록하고 공급해요.',
    Icon: Package,
  },
  {
    value: 'BRAND',
    label: '브랜드',
    description: '소재를 구매해 제품을 생산해요.',
    Icon: PaintBrush,
  },
  {
    value: 'PROCESSOR',
    label: '가공업체',
    description: '소재 가공 요청을 수행해요.',
    Icon: Factory,
  },
] as const satisfies ReadonlyArray<{
  value: BusinessRole
  label: string
  description: string
  Icon: typeof Package
}>

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const businessNumberPattern = /^\d{3}-?\d{2}-?\d{5}$/
const companyPhonePattern = /^0\d{1,2}-?\d{3,4}-?\d{4}$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,}$/

export function BusinessSignupForm({
  onSubmit,
  isSubmitting = false,
}: BusinessSignupFormProps) {
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false)
  const [isSearchingAddress, setIsSearchingAddress] = useState(false)
  const [addressSearchError, setAddressSearchError] = useState('')
  const {
    register,
    handleSubmit,
    control,
    setFocus,
    setValue,
    formState: { errors },
  } = useForm<BusinessSignupFormValues>({
    defaultValues: {
      agreeTerms: false,
      agreePrivacy: false,
      agreeMarketing: false,
    },
  })
  const password = useWatch({ control, name: 'password' })
  const selectedRole = useWatch({ control, name: 'role' })

  const searchAddress = async () => {
    setAddressSearchError('')
    setIsSearchingAddress(true)
    try {
      await openKakaoPostcode(({ zonecode, address }) => {
        setValue('zipCode', zonecode, { shouldDirty: true, shouldValidate: true })
        setValue('address', address, { shouldDirty: true, shouldValidate: true })
        setFocus('addressDetail')
      })
    } catch {
      setAddressSearchError('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSearchingAddress(false)
    }
  }

  const submitForm = handleSubmit((values) => {
    return onSubmit({
      role: values.role,
      companyName: values.companyName,
      businessNumber: values.businessNumber,
      representativeName: values.representativeName,
      companyPhone: values.companyPhone,
      zipCode: values.zipCode,
      address: values.address,
      addressDetail: values.addressDetail,
      email: values.email,
      password: values.password,
      agreeTerms: values.agreeTerms,
      agreePrivacy: values.agreePrivacy,
      agreeMarketing: values.agreeMarketing,
    })
  })

  return (
    <form className="auth-entry-form business-signup" onSubmit={submitForm} noValidate>
      <fieldset className="business-signup__roles">
        <legend>업체 유형</legend>
        <p>가입 후 이용할 업무에 맞는 유형을 선택해 주세요.</p>
        <div className="business-signup__role-list">
          {businessRoles.map(({ value, label, description, Icon }) => (
            <label
              className={`business-signup__role${selectedRole === value ? ' business-signup__role--selected' : ''}`}
              key={value}
            >
              <input
                type="radio"
                value={value}
                {...register('role', { required: '업체 유형을 선택해 주세요.' })}
              />
              <Icon aria-hidden="true" />
              <strong>{label}</strong>
              <span>{description}</span>
            </label>
          ))}
        </div>
        {errors.role ? <p className="business-signup__role-error" role="alert">{errors.role.message}</p> : null}
      </fieldset>

      <div className="business-signup__section">
        <h2>업체 정보</h2>
        <div className="consumer-signup__grid">
          <Input
            label="업체명"
            autoComplete="organization"
            placeholder="업체명을 입력해 주세요."
            error={errors.companyName?.message}
            {...register('companyName', {
              required: '업체명을 입력해 주세요.',
              validate: (value) => value.trim().length >= 2 || '업체명은 2자 이상 입력해 주세요.',
            })}
          />
          <Input
            label="대표자명"
            placeholder="대표자명을 입력해 주세요."
            error={errors.representativeName?.message}
            {...register('representativeName', {
              required: '대표자명을 입력해 주세요.',
              validate: (value) => value.trim().length >= 2 || '대표자명은 2자 이상 입력해 주세요.',
            })}
          />
        </div>
        <div className="consumer-signup__grid">
          <Input
            label="사업자등록번호"
            inputMode="numeric"
            maxLength={12}
            placeholder="123-45-67890"
            error={errors.businessNumber?.message}
            {...register('businessNumber', {
              required: '사업자등록번호를 입력해 주세요.',
              onChange: (event) => {
                event.target.value = formatBusinessNumber(event.target.value)
              },
              pattern: {
                value: businessNumberPattern,
                message: '올바른 사업자등록번호를 입력해 주세요.',
              },
            })}
          />
          <Input
            label="업체 연락처"
            type="tel"
            inputMode="tel"
            maxLength={13}
            autoComplete="tel"
            placeholder="02-1234-5678"
            error={errors.companyPhone?.message}
            {...register('companyPhone', {
              required: '업체 연락처를 입력해 주세요.',
              onChange: (event) => {
                event.target.value = formatPhoneNumber(event.target.value)
              },
              pattern: {
                value: companyPhonePattern,
                message: '올바른 업체 연락처를 입력해 주세요.',
              },
            })}
          />
        </div>
        <div className="consumer-signup__grid">
          <Input
            label="우편번호"
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="06236"
            readOnly
            error={errors.zipCode?.message}
            {...register('zipCode', {
              required: '우편번호를 입력해 주세요.',
              maxLength: { value: 20, message: '우편번호는 20자 이하로 입력해 주세요.' },
            })}
          />
          <div className="business-signup__address-search">
            <Input
              label="업체 주소"
              autoComplete="street-address"
              placeholder="주소 검색을 이용해 주세요."
              readOnly
              error={errors.address?.message}
              {...register('address', {
                required: '업체 주소를 입력해 주세요.',
                validate: (value) => value.trim().length >= 5 || '주소를 5자 이상 입력해 주세요.',
              })}
            />
            <button type="button" onClick={() => void searchAddress()} disabled={isSearchingAddress}>
              <MagnifyingGlass aria-hidden="true" />
              {isSearchingAddress ? '불러오는 중' : '주소 검색'}
            </button>
          </div>
        </div>
        {addressSearchError ? <p className="business-signup__address-error" role="alert">{addressSearchError}</p> : null}
        <Input
          label="상세 주소"
          autoComplete="address-line2"
          placeholder="상세 주소를 입력해 주세요. (선택)"
          error={errors.addressDetail?.message}
          {...register('addressDetail', {
            maxLength: { value: 255, message: '상세 주소는 255자 이하로 입력해 주세요.' },
          })}
        />
      </div>

      <div className="business-signup__section">
        <h2>로그인 정보</h2>
        <Input
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="업체에서 사용할 이메일 주소를 입력해 주세요."
          error={errors.email?.message}
          {...register('email', {
            required: '이메일을 입력해 주세요.',
            pattern: { value: emailPattern, message: '올바른 이메일 주소를 입력해 주세요.' },
          })}
        />
        <div className="consumer-signup__grid">
          <div className="consumer-signup__password-field">
            <Input
              label="비밀번호"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="영문, 숫자, 특수문자를 포함해 8자 이상"
              error={errors.password?.message}
              {...register('password', {
                required: '비밀번호를 입력해 주세요.',
                pattern: {
                  value: passwordPattern,
                  message: '영문, 숫자, 특수문자를 포함해 8자 이상 입력해 주세요.',
                },
              })}
            />
            <button
              className="consumer-signup__password-toggle"
              type="button"
              aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
              onClick={() => setShowPassword((visible) => !visible)}
            >
              {showPassword ? <EyeSlash aria-hidden="true" /> : <Eye aria-hidden="true" />}
            </button>
          </div>
          <div className="consumer-signup__password-field">
            <Input
              label="비밀번호 확인"
              type={showPasswordConfirm ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="비밀번호를 다시 입력해 주세요."
              error={errors.passwordConfirm?.message}
              {...register('passwordConfirm', {
                required: '비밀번호를 다시 입력해 주세요.',
                validate: (value) => value === password || '비밀번호가 일치하지 않습니다.',
              })}
            />
            <button
              className="consumer-signup__password-toggle"
              type="button"
              aria-label={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
              onClick={() => setShowPasswordConfirm((visible) => !visible)}
            >
              {showPasswordConfirm ? <EyeSlash aria-hidden="true" /> : <Eye aria-hidden="true" />}
            </button>
          </div>
        </div>
      </div>

      <fieldset className="consumer-signup__agreements">
        <legend>약관 동의</legend>
        <label>
          <input type="checkbox" {...register('agreeTerms', { required: '이용약관에 동의해 주세요.' })} />
          <span><strong>[필수]</strong> 이용약관 동의</span>
        </label>
        {errors.agreeTerms ? <p role="alert">{errors.agreeTerms.message}</p> : null}
        <label>
          <input
            type="checkbox"
            {...register('agreePrivacy', { required: '개인정보 수집 및 이용에 동의해 주세요.' })}
          />
          <span><strong>[필수]</strong> 개인정보 수집 및 이용 동의</span>
        </label>
        {errors.agreePrivacy ? <p role="alert">{errors.agreePrivacy.message}</p> : null}
        <label>
          <input type="checkbox" {...register('agreeMarketing')} />
          <span><strong>[선택]</strong> 마케팅 및 서비스 소식 수신 동의</span>
        </label>
      </fieldset>

      <Button type="submit" loading={isSubmitting} fullWidth>사업자회원 가입하기</Button>
    </form>
  )
}
