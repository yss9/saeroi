# 업체 검색 및 Brand 요청 화면

## 업체 검색

`GET /processors/search` — 활성 BRAND 전용. 프론트 `/api` 프록시용 `/api/v1/processors/search`도 지원한다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| materialType | 예 | COTTON, COTTON_MIXED, DENIM, POLYESTER, SILK, VISCOSE, WOOL, ETC |
| quantity | 예 | 양의 정수 g, Long 범위. 상한은 없다 — 많으면 오래 걸릴 뿐이다 |
| processTypes | 예 | 중복 없는 공정 1~8개. 쉼표 구분 또는 반복 파라미터 |
| page | 아니오 | 0부터 시작, 기본 0 |
| size | 아니오 | 1~50, 기본 12 |

예: `/processors/search?materialType=DENIM&quantity=5000&processTypes=CLEANING,CUTTING`

응답 `ApiResponse.data`:

```json
{
  "items": [{
    "processorId": 2,
    "name": "리뉴 텍스타일",
    "region": "서울 성동구",
    "dailyCapacity": 1000,
    "pricePerKg": 3000,
    "estimatedPrice": 15000,
    "estimatedDays": 8,
    "estimatedLeadTimes": [
      { "processType": "CLEANING", "leadDays": 3 },
      { "processType": "CUTTING", "leadDays": 5 }
    ]
  }],
  "total": 1,
  "page": 0,
  "size": 12
}
```

- 활성 PROCESSOR 및 등록 프로필만 노출한다.
- 해당 소재에서 **모든** 선택 공정을 제공해야 한다. 하나라도 없으면 목록에서 빠진다 — 반쯤 맡길 수는 없다.
- **수량 상한은 없앴다.** 예전에는 `max_quantity`를 넘으면 업체가 아예 사라져서, 브랜드는 "왜 아무도 안 나오지" 만 보고 이유를 알 수 없었다. 지금은 소요일이 늘어난다.
- 하루 처리량이 0인 조합은 제외한다. 소요일의 분모라 며칠 걸리는지 답할 수 없다.
- `pricePerKg`는 선택한 공정 **전부를 합한** kg 당 단가, `dailyCapacity`는 그중 **가장 느린** 공정의 하루 처리량(병목)이다.
- `estimatedPrice`는 공정마다 `⌈단가 × 수량(g) ÷ 1000⌉`을 구해 더한 값이고, `estimatedDays`는 공정별 `⌈수량 ÷ 하루 처리량⌉`의 합이다. 세척한 뒤에 선별하는 것이지 동시에 하는 것이 아니므로 기간은 더해진다.
- **화면은 곱하지 않는다.** 단가와 수량만 주고 화면이 계산하게 하면 올림이 어긋나는 순간 목록 금액과 결제 금액이 달라지고, 사용자는 결제 직전에야 그것을 안다. 요청 생성이 같은 식으로 `fee`를 확정하므로 두 값이 일치한다.
- 필터 없이 둘러볼 때는 수량이 없어 `estimatedPrice`·`estimatedDays`가 **null**이다. 0으로 채우면 "공짜로 오늘 끝난다"가 되어 값이 없다는 사실보다 나쁜 거짓말이 된다. 그때 `pricePerKg`는 등록된 조합 중 가장 싼 값, `dailyCapacity`는 가장 큰 값이다.
- 결과 없음은 HTTP200, 빈 items, total0이다. UI에서 조건 완화 안내를 보여준다.
- 조회는 재고를 예약하지 않는다. 실제 요청 생성 때 업체 조건과 가용량을 다시 검증한다.

## 내 가공 가능 소재

`GET /processing-requests/materials` (`/api/v1/processing-requests/materials`)

활성 BRAND의 보유 소재만 반환한다. data는 `materialId, materialCode, name, materialType, quantity, lockedQuantity, availableQuantity, completedProcessTypes` 배열이다.

`completedProcessTypes`는 그 소재가 **혈통을 따라 지금까지 거친** 가공 종류다(확정된 가공만 센다). 화면이 "이 소재는 세척까지 됐으니 이번엔 선별을 맡기자"를 그 자리에서 판단하는 근거이고, 이 값이 없으면 같은 소재를 보유 소재 목록(`GET /api/materials/me`)에서 한 번 더 읽어야 한다. 가공 전이면 빈 배열이고 null이 아니다. 보유량이 양수인 ACTIVE 소재를 포함하고, 예약량은 모든 ACTIVE Lock의 합계다. 사용 가능량이 0인 소재도 보유/예약 현황 확인을 위해 포함한다.

신규 DB는 `schema.sql`을 사용한다. 기존 DB에는 `src/main/resources/db/migrations/2026-09-19-processing-without-quotes.sql`을 수동 적용해야 한다 — `processor_lead_time` 테이블을 지우고 `processor_capability`의 수량 구간을 하루 처리량·단가로 바꾼다.

## 로컬에서 실제 API 연동 테스트

운영 DB 대신 테스트 클래스패스의 H2 메모리 DB로 실행한다. 테스트 전용 main/시드/JWT는 운영 JAR에 포함되지 않는다. 서버는 127.0.0.1:8080에만 바인딩한다. 기존 서버가 해당 포트를 사용하면 먼저 해당 서버의 용도를 확인하고 다른 작업을 종료하지 않는다.

```powershell
./scripts/start-processing-preview.ps1 -FrontendPath '../S15P21E104_front' -JavaHome '설치한 JDK 21 이상 경로'
```

별도 터미널에서 프론트:

```powershell
npm ci
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

`http://localhost:5173/processing-preview.html`에서 테스트 계정으로 시작한다. 이 파일은 시드 사용자의 실제 JWT를 로컬 auth 저장소에 설정하며, 백엔드의 기존 JWT 검증과 DB 권한 확인을 그대로 거친다. 프론트 `.gitignore`에 포함된 임시 파일로 배포·커밋하지 않는다. 토큰은8시간 유효하며 만료되면 테스트 서버를 재시작한다. Vite도 loopback에만 바인딩해야 한다.

시드: 폐데님30000g 중5000g 예약, 면15000g, 가공업체3곳. 세척·해체·재단 검색은 결과가 있고 염색은 결과가 없다. 요청을 만들면 예약량이 증가하고 취소하면 복원된다. DB는 재시작하면 초기화된다. 실시간 블록체인 동기화·온체인 거래는 이 테스트에 포함하지 않는다.
