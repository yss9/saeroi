import { forwardRef, useId, type InputHTMLAttributes } from 'react'

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> {
  label?: string
  /** 검증 실패 문구. 있으면 필드가 오류 상태로 표시된다. */
  error?: string
  hint?: string
}

/**
 * ref 를 넘겨받는 이유: React Hook Form 의 `register()` 가 ref 로 DOM 을 잡는다.
 * forwardRef 가 없으면 RHF 와 함께 쓸 수 없다.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, id, ...rest },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const describedById = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined

  return (
    <div className="ui-field">
      {label && (
        <label className="ui-field__label" htmlFor={inputId}>
          {label}
        </label>
      )}
      <input
        {...rest}
        id={inputId}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedById}
        className={`ui-field__control${error ? ' ui-field__control--error' : ''}`}
      />
      {error ? (
        <p className="ui-field__error" id={`${inputId}-error`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="ui-field__hint" id={`${inputId}-hint`}>
          {hint}
        </p>
      ) : null}
    </div>
  )
})
