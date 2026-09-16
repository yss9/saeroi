import { forwardRef, useId, type SelectHTMLAttributes } from 'react'

export interface SelectOption<T extends string = string> {
  value: T
  label: string
  disabled?: boolean
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  label?: string
  error?: string
  options: readonly SelectOption[]
  /** 선택 전 표시할 항목. 값은 빈 문자열이다. */
  placeholder?: string
  /** 라벨만으로 부족한 설명. 오류가 있으면 오류가 이 자리를 쓴다 (Input 과 같다). */
  hint?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, options, placeholder, hint, id, ...rest },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const describedById = error ? `${selectId}-error` : hint ? `${selectId}-hint` : undefined

  return (
    <div className="ui-field">
      {label && (
        <label className="ui-field__label" htmlFor={selectId}>
          {label}
        </label>
      )}
      <select
        {...rest}
        id={selectId}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedById}
        className={`ui-field__control${error ? ' ui-field__control--error' : ''}`}
      >
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
      {error ? (
        <p className="ui-field__error" id={`${selectId}-error`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="ui-field__hint" id={`${selectId}-hint`}>
          {hint}
        </p>
      ) : null}
    </div>
  )
})
