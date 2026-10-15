import { useEffect, useRef, type ReactNode } from 'react'

export interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  /**
   * 제목 아래 한 줄. **무엇에 대한 창인지**를 적는 자리다.
   *
   * 제목에 이어 붙이면("블랙진 · 제품 등록") 한 줄 안에서 하는 일과 대상이 섞여, 무엇을
   * 하는 창인지 읽는 데 눈이 한 번 멈춘다. 줄을 나누고 색을 달리 둔다.
   */
  subtitle?: ReactNode
  children: ReactNode
  footer?: ReactNode
  /** 배경 클릭으로 닫히지 않게 한다. 진행 중인 트랜잭션 모달에 쓴다. */
  dismissible?: boolean
}

/**
 * `<dialog>` 를 쓰는 이유: 포커스 가두기, Esc 닫기, 배경 비활성화를 브라우저가
 * 해준다. 직접 구현하면 접근성 처리(포커스 트랩)를 빠뜨리기 쉽다.
 */
export function Modal({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  dismissible = true,
}: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  // 외부 상태(open)를 DOM 명령형 API 와 동기화하는 것은 useEffect 의 정당한 용도다.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open])

  /*
    **열 때마다 맨 위에서 시작한다.**

    같은 창을 닫았다 다시 열면 `<dialog>` 는 지난번 내려둔 자리를 그대로 들고 있다 —
    거래 하나를 훑어보고 닫은 뒤 다른 거래를 열면 중간부터 보인다.

    여는 순간 한 번, 그리고 다음 프레임에 한 번 더 되돌린다. 안의 내용이 대개 조회 중
    (짧은 상태)으로 그려졌다가 도착한 뒤에 길어지는데, 그 사이에 브라우저가 스크롤을
    복원하는 경우가 있다.
  */
  useEffect(() => {
    if (!open) return
    const dialog = dialogRef.current
    if (!dialog) return
    const toTop = () => {
      dialog.scrollTop = 0
      // 안쪽에 따로 스크롤을 잡은 화면도 있다. jsdom 에는 scrollTo 가 없어 속성으로 쓴다.
      const body = dialog.querySelector<HTMLElement>('.ui-modal__body')
      if (body) body.scrollTop = 0
    }
    toTop()
    const frame = requestAnimationFrame(toTop)
    return () => cancelAnimationFrame(frame)
  }, [open, title])

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
          <div className="ui-modal__heading">
            <h2 className="ui-modal__title" id="ui-modal-title">
              {title}
            </h2>
            {subtitle !== undefined && <p className="ui-modal__subtitle">{subtitle}</p>}
          </div>
          {dismissible && (
            <button type="button" className="ui-modal__close" onClick={onClose} aria-label="닫기">
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
