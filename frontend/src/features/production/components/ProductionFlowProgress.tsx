import { PRODUCTION_FLOW_STEPS, type ProductionFlowStep } from '../services/productionFlow.ts'

/**
 * 생산 확정의 다섯 단계와 지금 어디인지. 서명 창이 연달아 뜨는 동안 무엇이 진행 중인지 보여준다.
 *
 * `doneSteps` 는 이미 끝난 단계(체인·서버가 말한 것), `currentStep` 은 진행 중이거나 멈춘 단계다.
 */
export function ProductionFlowProgress({ doneSteps, currentStep, running }: {
  doneSteps: ReadonlySet<ProductionFlowStep>
  currentStep: ProductionFlowStep | null
  running: boolean
}) {
  return (
    <ol className="chain-steps production-flow" aria-label="생산 기록 진행 상황">
      {PRODUCTION_FLOW_STEPS.map(({ step, label }) => {
        const done = doneSteps.has(step)
        const active = !done && step === currentStep
        return (
          <li key={step} className={done ? 'chain-steps__done' : active ? 'production-flow__active' : undefined}>
            <div className="chain-steps__head">
              <span>{label}</span>
              {done && <span className="chain-steps__badge">완료</span>}
              {active && <span className="chain-steps__badge production-flow__badge">{running ? '진행 중' : '멈춤'}</span>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
