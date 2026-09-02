import { useEffect, useRef, type ReactNode } from 'react'

export interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  /** 배경 클릭으로 닫히지 않게 한다. 진행 중인 트랜잭션 모달에 쓴다. */
  dismissible?: boolean
}

/**
 * `<dialog>` 를 쓰는 이유: 포커스 가두기, Esc 닫기, 배경 비활성화를 브라우저가
 * 해준다. 직접 구현하면 접근성 처리(포커스 트랩)를 빠뜨리기 쉽다.
 */
export function Modal({ open, onClose, title, children, footer, dismissible = true }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  // 외부 상태(open)를 DOM 명령형 API 와 동기화하는 것은 useEffect 의 정당한 용도다.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={dialogRef}
      className="ui-modal"
      aria-labelledby="ui-modal-title"
      // Esc 키는 dialog 가 자체적으로 처리하므로 cancel 을 가로채 상태를 맞춘다.
      onCancel={(event) => {
        if (!dismissible) {
          event.preventDefault()
          return
        }
        onClose()
      }}
      onClick={(event) => {
        // 배경(::backdrop) 클릭은 dialog 자신을 target 으로 준다.
        if (dismissible && event.target === dialogRef.current) onClose()
      }}
    >
      <div className="ui-modal__panel">
        <header className="ui-modal__header">
          <h2 className="ui-modal__title" id="ui-modal-title">
            {title}
          </h2>
          {dismissible && (
            <button className="ui-modal__close" onClick={onClose} aria-label="닫기">
              ×
            </button>
          )}
        </header>
        <div className="ui-modal__body">{children}</div>
        {footer && <footer className="ui-modal__footer">{footer}</footer>}
      </div>
    </dialog>
  )
}
