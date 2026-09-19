/**
 * AI 어시스턴트를 별도 창으로 여는 버튼.
 *
 * <h2>왜 별도 창인가</h2>
 *
 * 담당자가 기존 화면(소재 목록, 가공 요청)을 보면서 대화할 수 있어야 한다. 대화창이 전체
 * 화면을 차지하면 에이전트가 말하는 내용을 화면에서 확인할 수 없다.
 *
 * <h2>창은 하나만 열린다</h2>
 *
 * `window.open` 의 두 번째 인자로 <b>이름</b>을 준다. 같은 이름으로 다시 열면 브라우저가 새
 * 창을 만들지 않고 기존 창을 앞으로 가져온다 — 같은 대화를 두 곳에서 여는 문제를 브라우저가
 * 막아준다. 서버에서도 담당자당 활성 대화가 하나뿐이라 두 겹으로 보호된다.
 */
const WINDOW_NAME = 'saeroi-agent'

/**
 * 창 크기만 지정하고 툴바를 숨기지 않는다.
 *
 * <p><b>MetaMask 때문이다.</b> 지갑이 잠겨 있으면 서명 요청이 확장 아이콘의 배지로 대기하는데,
 * {@code toolbar=no} 로 열면 그 아이콘이 보이지 않아 담당자가 아무 반응 없는 화면만 보게 된다.
 * 주소창이 보이는 것이 조금 덜 깔끔해도, 서명이 막히는 것보다 낫다.
 *
 * <p>Chrome 은 크기를 지정하면 어차피 팝업형 창을 만들어 확장 아이콘을 노출하지 않는다.
 * 그래서 {@code WalletSignCard} 가 서명 전에 잠금 여부를 미리 확인해 안내한다 — 이쪽이
 * 실질적인 방어선이다.
 */
const WINDOW_FEATURES = 'width=480,height=860'

export function AgentLauncher({ label = 'AI 어시스턴트' }: { label?: string }) {
  function open() {
    const url = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/agent`
    const opened = window.open(url, WINDOW_NAME, WINDOW_FEATURES)
    // 팝업이 차단되면 null 이 온다. 조용히 아무 일도 안 일어나면 담당자가 버튼이 고장난
    // 줄 안다 — 같은 탭으로라도 열어 준다.
    if (!opened) {
      window.location.href = url
      return
    }
    opened.focus()
  }

  return (
    <button type="button" className="agent-launcher" onClick={open}>
      {label}
    </button>
  )
}
