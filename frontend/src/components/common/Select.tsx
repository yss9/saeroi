import { forwardRef, useId, type SelectHTMLAttributes } from 'react'

export interface SelectOption<T extends string = string> {
  value: T
  label: string
  disabled?: boolean
  /**
   * 이 항목이 속한 묶음의 이름.
   *
   * **한 줄 안에서 두 가지를 말해야 할 때 쓴다.** `<option>` 안의 글자는 일부만 색이나
   * 굵기를 달리할 수 없어서, "이름 · 이유" 처럼 붙여 적으면 어디까지가 이름인지 읽히지
   * 않는다. 이유를 묶음 이름으로 올리면 브라우저가 그것을 굵게 그리고 항목은 들여쓴다.
   *
   * 붙인 순서대로 묶인다 — 같은 이름이 떨어져 있으면 묶음도 둘로 나뉜다.
   */
  group?: string
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

function renderOption(option: SelectOption) {
  return (
    <option key={option.value} value={option.value} disabled={option.disabled}>
      {option.label}
    </option>
  )
}

/**
 * 이어진 항목을 묶음으로 자른다.
 *
 * 순서를 바꾸지 않는다 — 어느 묶음이 먼저 오는지는 부르는 쪽이 정할 일이고, 여기서
 * 다시 정렬하면 같은 목록이 화면마다 다른 순서로 나온다.
 */
function groupsOf(
  options: readonly SelectOption[],
): { group: string | undefined; options: SelectOption[] }[] {
  const sections: { group: string | undefined; options: SelectOption[] }[] = []
  for (const option of options) {
    const last = sections.at(-1)
    if (last !== undefined && last.group === option.group) last.options.push(option)
    else sections.push({ group: option.group, options: [option] })
  }
  return sections
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
        {groupsOf(options).map((section, index) =>
          section.group === undefined ? (
            section.options.map(renderOption)
          ) : (
            <optgroup key={`${section.group}-${index}`} label={section.group}>
              {section.options.map(renderOption)}
            </optgroup>
          ),
        )}
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
