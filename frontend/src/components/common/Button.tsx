import type { ButtonHTMLAttributes, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'danger'
export type ButtonSize = 'sm' | 'md' | 'lg'

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  variant?: ButtonVariant
  size?: ButtonSize
  /** 진행 중 표시. true 면 자동으로 비활성화된다. */
  loading?: boolean
  fullWidth?: boolean
  children: ReactNode
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  disabled,
  type = 'button',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      // loading 중 클릭을 막는 책임을 호출부에 넘기지 않는다. 중복 제출은
      // 주문·트랜잭션에서 되돌리기 어려운 사고로 이어진다.
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        'ui-button',
        `ui-button--${variant}`,
        `ui-button--${size}`,
        fullWidth ? 'ui-button--full' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {loading ? <span className="ui-button__spinner" aria-hidden="true" /> : null}
      {children}
    </button>
  )
}
