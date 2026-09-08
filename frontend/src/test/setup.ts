import '@testing-library/jest-dom/vitest'

// jsdom 에 `<dialog>` 의 showModal / close 가 없다.
//
// `components/common/Modal` 이 `<dialog>` 를 쓰는 이유는 포커스 가두기·Esc 닫기를
// 브라우저가 해주기 때문인데, jsdom 은 그 메서드를 구현하지 않아 모달이 열리는 순간
// "dialog.showModal is not a function" 으로 테스트가 죽는다.
//
// 그래서 최소한만 채운다 — 열림 여부를 `open` 속성으로 반영하는 것까지다.
// 포커스 트랩이나 ::backdrop 은 흉내내지 않는다. 그건 브라우저가 하는 일이고,
// 여기서 가짜로 구현하면 통과했지만 실제로는 다른 동작을 검증하게 된다.
//
// 이미 구현이 있으면 손대지 않는다. jsdom 이 지원하는 버전으로 올라가면 이 블록은
// 자동으로 비활성화된다.
if (typeof HTMLDialogElement !== 'undefined') {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open')
    }
  }
}

// jsdom 에 `URL.createObjectURL` / `revokeObjectURL` 이 없다.
//
// 사용자가 고른 파일을 미리 보여주거나, 인증이 필요한 이미지를 fetch 로 받아 화면에 그리려면
// blob URL 이 필요하다 (제품 대표 이미지·증빙 사진이 그렇다). jsdom 은 이 API 를 구현하지
// 않아서, 호출하는 순간 이벤트 핸들러가 TypeError 로 죽고 그 뒤 코드가 실행되지 않는다 —
// 화면은 아무 반응이 없고 테스트는 "요청이 안 나갔다" 로만 보인다.
//
// 그래서 형태만 맞춘 값을 돌려준다. 실제 blob 을 만들지는 않으므로 이 URL 로 이미지를
// 불러오는 것까지 검증할 수는 없다 — 그건 브라우저의 일이다.
if (typeof URL.createObjectURL !== 'function') {
  let issued = 0
  URL.createObjectURL = () => `blob:jsdom/${(issued += 1)}`
  URL.revokeObjectURL = () => {}
}
