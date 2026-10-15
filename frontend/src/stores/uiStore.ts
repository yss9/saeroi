// 전역 UI 상태. 최소한만 둔다.
//
// 한 화면 안에서만 쓰는 Tab/Dropdown/Toggle 은 여기 넣지 말고 useState 를 쓴다.
// "라우트가 바뀌어도 유지되어야 하거나, 멀리 떨어진 컴포넌트끼리 공유해야 하는" UI 상태만 여기 온다.

import { create } from 'zustand'

/** 전역 Modal 식별자. 새 전역 모달이 생기면 여기에 추가한다. */
export type GlobalModal = 'wallet-connect' | 'confirm' | null

interface UiState {
  isSidebarOpen: boolean
  activeModal: GlobalModal

  toggleSidebar: () => void
  setSidebarOpen: (open: boolean) => void
  openModal: (modal: NonNullable<GlobalModal>) => void
  closeModal: () => void
}

export const useUiStore = create<UiState>((set) => ({
  isSidebarOpen: false,
  activeModal: null,

  toggleSidebar: () => set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
  setSidebarOpen: (open) => set({ isSidebarOpen: open }),
  openModal: (modal) => set({ activeModal: modal }),
  closeModal: () => set({ activeModal: null }),
}))
