// 공통 컴포넌트 barrel.
//
// 사용: import { Button, Modal } from '../../components/common'
//
// ⚠️ features 끼리는 barrel 을 만들지 않는다. Feature 간 barrel 은 순환 의존성을
// 만들기 쉽고, 한 파일만 필요해도 도메인 전체가 번들에 끌려온다.
// 공통 컴포넌트는 도메인에 의존하지 않으므로 순환이 생길 수 없어 예외로 둔다.

export { BackLink, type BackLinkProps } from './BackLink.tsx'
export { Button, type ButtonProps, type ButtonVariant, type ButtonSize } from './Button.tsx'
export { ConfirmModal, type ConfirmModalProps } from './ConfirmModal.tsx'
export { EmptyState, type EmptyStateProps } from './EmptyState.tsx'
export { ErrorMessage, type ErrorMessageProps } from './ErrorMessage.tsx'
export { FileUploader, type FileUploaderProps } from './FileUploader.tsx'
export {
  validateFiles,
  isPreviewableImage,
  rejectionMessage,
  groupRejections,
  type FileRejection,
  type FileRejectionReason,
  type FileValidationPolicy,
} from './fileValidation.ts'
export { Input, type InputProps } from './Input.tsx'
export { LocalFilePreview, type LocalFilePreviewProps } from './LocalFilePreview.tsx'
export { Loading, type LoadingProps } from './Loading.tsx'
export { Modal, type ModalProps } from './Modal.tsx'
export { OnChainPanel, TransactionHash, type OnChainPanelProps } from './OnChainPanel.tsx'
export { QuantityInput, type QuantityInputProps } from './QuantityInput.tsx'
export { QUANTITY_UNIT_LABEL, type QuantityUnit } from './quantity.ts'
export { Select, type SelectProps, type SelectOption } from './Select.tsx'
export { StatusBadge, type StatusBadgeProps, type StatusTone } from './StatusBadge.tsx'
export { TransactionStatus, type TransactionStatusProps } from './TransactionStatus.tsx'
export {
  VerificationBadge,
  type VerificationBadgeProps,
  type VerificationState,
} from './VerificationBadge.tsx'
export { WalletChip, type WalletChipProps } from './WalletChip.tsx'
