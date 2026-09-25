import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * 대화 한 칸의 본문.
 *
 * <h2>왜 마크다운으로 그리나</h2>
 *
 * 예전에는 `<p>{content}</p>` 로 그대로 찍었다. 그런데 모델은 **굵게**와 표를 자연스럽게
 * 쓴다 — 담당자 화면에는 별표와 파이프가 그대로 보였다. 업체별 단가·소요일처럼 **나란히
 * 놓고 비교해야 읽히는 내용**이 있어서, 표를 쓰지 말라고 막기보다 제대로 그리는 쪽이 맞다.
 *
 * `remark-gfm` 이 표를 맡는다. 기본 마크다운에는 표가 없다.
 *
 * <h2>물결표 하나는 취소선이 아니다</h2>
 *
 * `remark-gfm` 은 기본값으로 `~글자~` 도 취소선으로 그린다. 그런데 한국어에서 물결표는 범위다 — 모델이 "한 벌에
 * 0.6~0.8kg 이 든다고 보면, 10벌에 6~8kg" 이라고 쓰자 두 물결표 사이가 통째로 그어져 숫자를 읽을 수 없었다.
 * 그래서 `~~글자~~` 만 취소선으로 본다(`singleTilde: false`).
 *
 * <h2>안전</h2>
 *
 * `react-markdown` 은 **원시 HTML 을 그리지 않는다.** `rehype-raw` 같은 플러그인을 붙이지
 * 않는 한 `<script>` 나 `<img onerror=…>` 는 글자로 남는다. 대화 내용은 모델이 쓰고 모델은
 * 도구 결과를 읽으므로, 언젠가 판매 글 제목 같은 남의 입력이 섞여 들어올 수 있다 — 그때도
 * 실행되지 않아야 한다. 그래서 플러그인은 `remark-gfm` 하나만 둔다.
 *
 * <h2>링크</h2>
 *
 * 링크는 새 탭으로 열고 `noreferrer` 를 붙인다. 대화창은 작업 중인 화면이라, 링크를 눌러
 * 같은 탭이 떠나면 진행 중이던 서명이나 결제가 끊긴다.
 *
 * <h2>본문이 없을 수 있다</h2>
 *
 * tool 을 부르기로 한 턴의 메시지는 본문이 비어 있다(`AgentMessage.assistantToolCall`).
 * 그래서 `null` 을 받는다.
 */
export function AgentMessageBody({ content }: { content: string | null }) {
  return (
    <div className="agent-message__body">
      <Markdown
        remarkPlugins={[[remarkGfm, { singleTilde: false }]]}
        components={{
          a: ({ children, href }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ),
          // 표는 가로로 넘칠 수 있다. 대화 칸이 좁아서 감싸 두지 않으면 말풍선이 찢어진다.
          table: ({ children }) => (
            <div className="agent-message__table">
              <table>{children}</table>
            </div>
          ),
        }}
      >
        {content ?? ''}
      </Markdown>
    </div>
  )
}
