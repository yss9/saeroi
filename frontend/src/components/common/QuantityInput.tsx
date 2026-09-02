import { useId } from 'react'
import { QUANTITY_UNIT_LABEL, type QuantityUnit } from './quantity.ts'

export interface QuantityInputProps {
  value: number | null
  unit: QuantityUnit
  onChange: (value: number | null) => void
  label?: string
  error?: string
  min?: number
  max?: number
  disabled?: boolean
}

export function QuantityInput({
  value,
  unit,
  onChange,
  label = '수량',
  error,
  min = 0,
  max,
  disabled,
}: QuantityInputProps) {
  const inputId = useId()

  return (
    <div className="ui-field">
      <label className="ui-field__label" htmlFor={inputId}>
        {label}
      </label>
      <div className="ui-quantity">
        <input
          id={inputId}
          className={`ui-field__control${error ? ' ui-field__control--error' : ''}`}
          type="number"
          inputMode="decimal"
          value={value ?? ''}
          min={min}
          max={max}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          // 빈 입력을 0 으로 바꾸지 않는다. "아직 입력 안 함(null)" 과 "0 을 입력함"
          // 은 검증에서 다르게 다뤄야 한다.
          onChange={(event) => {
            const raw = event.target.value
            onChange(raw === '' ? null : Number(raw))
          }}
        />
        <span className="ui-quantity__unit">{QUANTITY_UNIT_LABEL[unit]}</span>
      </div>
      {error && (
        <p className="ui-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
