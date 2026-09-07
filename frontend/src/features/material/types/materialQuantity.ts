// 소재 수량 단위 — Backend `POST /api/materials` 의 `unit` 과 1:1 대응한다.
//
// 컴포넌트 파일과 분리한 이유는 `components/common/quantity.ts` 와 같다:
// 한 파일이 컴포넌트와 상수를 함께 export 하면 Vite Fast Refresh 가 그 모듈을
// 갱신할 때 상태를 잃는다 (react-refresh 규칙).
//
// ⚠️ `components/common/quantity.ts` 의 `QuantityUnit`('KG' | 'METER') 을 대체한다.
// 그 파일 TODO 가 "단위를 어디에 둘지 확정되면 features/material/types 로 옮기고
// Backend Enum 이름과 일치시킨다" 였고, 이 파일이 그 결과다.
//
// **'METER' 는 Backend 에 없다.** 소재 수량은 gram 정수가 원칙이고
// (`.harness/reference/02-domain-rules.md` 1절 "단위 — 예외 없다"),
// 길이는 `material.length_mm` 참고 metadata 다. 미터를 수량 단위로 두면
// gram 으로 환산할 방법이 없어 저장 자체가 불가능하다.

/** Backend `MaterialQuantityUnit` 과 값이 같아야 한다. */
export type MaterialQuantityUnit = 'G' | 'KG'

export const MATERIAL_QUANTITY_UNIT_LABEL: Record<MaterialQuantityUnit, string> = {
  G: 'g',
  KG: 'kg',
}

/**
 * 화면 입력 단위. 사용자는 kg 로 입력한다.
 *
 * 서버로는 **항상 gram 정수** 로 보낸다 — Backend `quantity` 가 정수만 받기 때문이다
 * (소수를 보내면 400). `20.5 kg` 같은 입력은 `utils/quantity.ts` 의 `kgToGram()` 으로
 * 환산해 `{ quantity: 20500, unit: 'G' }` 가 된다.
 *
 * 그래서 이 폼이 보내는 `unit` 은 실질적으로 항상 `'G'` 다. 그럼에도 API 가 단위를
 * 받는 것은 정수 kg 을 보내는 다른 클라이언트를 위한 것이고, 서버가 자릿수 착오를
 * 잡아주는 방어선이기도 하다.
 */
export const FORM_INPUT_UNIT = 'KG' as const

/** 서버 전송 단위. gram 정수로 환산해 보내므로 고정이다. */
export const WIRE_UNIT: MaterialQuantityUnit = 'G'
