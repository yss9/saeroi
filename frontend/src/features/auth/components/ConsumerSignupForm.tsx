import { Eye, EyeSlash, MagnifyingGlass } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Button, Input } from '../../../components/common/index.ts'
import type {
  ConsumerSignupFormValues,
  ConsumerSignupSubmitValues,
} from '../types/signup.types.ts'
import { openKakaoPostcode } from '../../../utils/kakaoPostcode.ts'
import { formatPhoneNumber } from '../../../utils/phone.ts'

interface ConsumerSignupFormProps {
  onSubmit: (values: ConsumerSignupSubmitValues) => void | Promise<void>
  isSubmitting?: boolean
}

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phonePattern = /^01[016789]-?\d{3,4}-?\d{4}$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,}$/

export function ConsumerSignupForm({
  onSubmit,
  isSubmitting = false,
}: ConsumerSignupFormProps) {
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
  } = useForm<ConsumerSignupFormValues>({
    defaultValues: {
      agreeTerms: false,
      agreePrivacy: false,
      agreeMarketing: false,
    },
  })

  const password = useWatch({ control, name: 'password' })

  /**
   * 주소 검색.
   *
   * **우편번호와 주소를 손으로 못 적게 한다.** 배송지로 쓰이는 값이라 오타 하나가 배송
   * 사고가 되고, 우편번호와 주소가 서로 다른 곳을 가리키는 조합은 사람이 만들기 쉽다.
   * 검색이 실패하면 조용히 넘기지 않는다 — 두 칸이 읽기 전용이라 사용자가 손으로 메울
   * 방법이 없고, 이유를 말하지 않으면 가입이 막힌 것처럼 보인다.
   */
  async function searchAddress() {
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
      name: values.name,
      email: values.email,
      phone: values.phone,
      zipCode: values.zipCode,
      address: values.address,
      addressDetail: values.addressDetail,
      password: values.password,
      agreeTerms: values.agreeTerms,
      agreePrivacy: values.agreePrivacy,
      agreeMarketing: values.agreeMarketing,
    })
  })

  return (
    <form className="auth-entry-form consumer-signup" onSubmit={submitForm} noValidate>
      <div className="consumer-signup__grid">
        <Input
          label="이름"
          autoComplete="name"
          placeholder="이름을 입력해 주세요."
          error={errors.name?.message}
          {...register('name', {
            required: '이름을 입력해 주세요.',
            validate: (value) => value.trim().length >= 2 || '이름은 2자 이상 입력해 주세요.',
          })}
        />
        <Input
          label="휴대전화 번호"
          type="tel"
          inputMode="tel"
          maxLength={13}
          autoComplete="tel"
          placeholder="010-1234-5678"
          error={errors.phone?.message}
          {...register('phone', {
            required: '휴대전화 번호를 입력해 주세요.',
            onChange: (event) => {
              event.target.value = formatPhoneNumber(event.target.value)
            },
            pattern: {
              value: phonePattern,
              message: '올바른 휴대전화 번호를 입력해 주세요.',
            },
          })}
        />
      </div>

      <Input
        label="이메일"
        type="email"
        autoComplete="email"
        placeholder="이메일 주소를 입력해 주세요."
        error={errors.email?.message}
        {...register('email', {
          required: '이메일을 입력해 주세요.',
          pattern: { value: emailPattern, message: '올바른 이메일 주소를 입력해 주세요.' },
        })}
      />

      {/*
        주소는 배송지다. 제품을 받을 곳이 없으면 주문을 받아도 보낼 수가 없다.
        사업자 가입과 같은 방식으로 검색해서 채운다 — 두 화면이 다른 방식으로 주소를 받으면
        같은 나라 주소인데 형식이 갈라진다.
      */}
      <div className="consumer-signup__grid">
        <Input
          label="우편번호"
          inputMode="numeric"
          autoComplete="postal-code"
          placeholder="06236"
          readOnly
          error={errors.zipCode?.message}
          {...register('zipCode', {
            required: '주소 검색으로 우편번호를 채워 주세요.',
            maxLength: { value: 20, message: '우편번호는 20자 이하로 입력해 주세요.' },
          })}
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

      <div className="consumer-signup__password-field">
        <Input
          label="비밀번호"
          type={showPassword ? 'text' : 'password'}
          autoComplete="new-password"
          placeholder="비밀번호를 입력해 주세요."
          hint="영문, 숫자, 특수문자를 조합해 8자 이상 입력해 주세요."
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

      <fieldset className="consumer-signup__agreements">
        <legend>약관 동의</legend>
        <label>
          <input
            type="checkbox"
            {...register('agreeTerms', { required: '이용약관에 동의해 주세요.' })}
          />
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
          <span><strong>[선택]</strong> 마케팅 및 이벤트 소식 수신 동의</span>
        </label>
      </fieldset>

      <Button type="submit" loading={isSubmitting} fullWidth>
        가입하기
      </Button>
    </form>
  )
}
