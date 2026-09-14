import { Bell, Check, WarningCircle } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  useNotifications,
  useReadAllNotifications,
  useReadNotification,
} from '../hooks/useNotifications.ts'
import type { BusinessNotification } from '../types/notification.types.ts'
import './notification-center.css'

interface NotificationCenterProps {
  enabled: boolean
}

export function NotificationCenter({ enabled }: NotificationCenterProps) {
  const [isOpen, setIsOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const notifications = useNotifications(enabled)
  const readNotification = useReadNotification()
  const readAllNotifications = useReadAllNotifications()
  const unreadCount = notifications.data?.unreadCount ?? 0

  useEffect(() => {
    if (!isOpen) return

    function closeOnOutsideClick(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setIsOpen(false)
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('pointerdown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isOpen])

  function openNotification(notification: BusinessNotification) {
    if (!notification.read) readNotification.mutate(notification.notificationId)
    setIsOpen(false)
    navigate(notification.targetPath)
  }

  return (
    <div className="notification-center" ref={rootRef}>
      <button
        type="button"
        className="notification-center__trigger"
        aria-label={unreadCount > 0 ? `알림 ${unreadCount}개` : '새 알림 없음'}
        aria-expanded={isOpen}
        aria-controls="business-notification-panel"
        onClick={() => setIsOpen((open) => !open)}
      >
        <Bell aria-hidden="true" weight={unreadCount > 0 ? 'fill' : 'regular'} />
        {unreadCount > 0 ? (
          <span className="notification-center__badge" aria-hidden="true">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        ) : null}
      </button>

      {isOpen ? (
        <section
          id="business-notification-panel"
          className="notification-center__panel"
          role="region"
          aria-label="알림 목록"
        >
          <header className="notification-center__heading">
            <div>
              <strong>알림</strong>
              <span>확인할 업무를 알려드려요</span>
            </div>
            <button
              type="button"
              disabled={unreadCount === 0 || readAllNotifications.isPending}
              onClick={() => readAllNotifications.mutate()}
            >
              <Check aria-hidden="true" />
              모두 읽음
            </button>
          </header>

          <div className="notification-center__content">
            {notifications.isLoading ? <p className="notification-center__state">알림을 불러오는 중입니다.</p> : null}
            {notifications.isError ? (
              <p className="notification-center__state notification-center__state--error">
                <WarningCircle aria-hidden="true" />
                알림을 불러오지 못했습니다.
              </p>
            ) : null}
            {!notifications.isLoading && !notifications.isError && notifications.data?.items.length === 0 ? (
              <p className="notification-center__state">새로운 알림이 없습니다.</p>
            ) : null}
            {!notifications.isLoading && !notifications.isError ? (
              <ul className="notification-center__list">
                {notifications.data?.items.map((notification) => (
                  <li key={notification.notificationId}>
                    <button
                      type="button"
                      className={notification.read ? 'notification-center__item' : 'notification-center__item notification-center__item--unread'}
                      onClick={() => openNotification(notification)}
                    >
                      <span className="notification-center__item-title">
                        {!notification.read ? <i aria-hidden="true" /> : null}
                        {notification.title}
                      </span>
                      <span className="notification-center__item-message">{notification.message}</span>
                      <time dateTime={notification.createdAt}>{formatNotificationDate(notification.createdAt)}</time>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  )
}

function formatNotificationDate(value: string): string {
  const normalized = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
