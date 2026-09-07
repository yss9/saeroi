import { Eye, EyeSlash } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Button, Input } from '../../../components/common/index.ts'
import type {
  ConsumerSignupFormValues,
  ConsumerSignupSubmitValues,
} from '../types/signup.types.ts'

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
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<ConsumerSignupFormValues>({
    defaultValues: {
      agreeTerms: false,
      agreePrivacy: false,
      agreeMarketing: false,
    },
  })

  const password = useWatch({ control, name: 'password' })
  const submitForm = handleSubmit((values) => {
    return onSubmit({
      name: values.name,
      email: values.email,
      phone: values.phone,
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
          autoComplete="tel"
          placeholder="010-1234-5678"
          error={errors.phone?.message}
          {...register('phone', {
            required: '휴대전화 번호를 입력해 주세요.',
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

      <div className="consumer-signup__password-field">
        <Input
          label="비밀번호"
          type={showPassword ? 'text' : 'password'}
          autoComplete="new-password"
          placeholder="영문, 숫자, 특수문자를 포함해 8자 이상"
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
