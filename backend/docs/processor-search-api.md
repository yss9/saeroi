# 업체 검색 및 Brand 요청 화면

## 업체 검색

`GET /processors/search` — 활성 BRAND 전용. 프론트 `/api` 프록시용 `/api/v1/processors/search`도 지원한다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| materialType | 예 | COTTON, COTTON_MIXED, DENIM, POLYESTER, SILK, VISCOSE, WOOL, ETC |
| quantity | 예 | 양의 정수 g, Long 범위 |
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
    "minQuantity": 1000,
    "maxQuantity": 100000,
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
- 해당 소재에서 **모든** 선택 공정을 제공하고 각 공정 수량 범위가 요청을 포함해야 한다.
- 표시 최소 수량은 공정별 최소값 중 최댓값, 표시 최대 수량은 최대값 중 최솟값이다.
- `processor_lead_time`은 소재·공정별 수량 구간이다. `max_quantity`는 포함 상한이며, 각 선택 공정에서 요청 무게 이상인 가장 작은 상한의 `lead_days`를 반환한다.
- 선택한 모든 공정에 적용 가능한 소요일 구간이 있는 업체만 노출한다. `estimatedLeadTimes`는 요청한 공정 순서대로 반환하며 합산하지 않는다.
- 검색 소요일은 비교용 정보다. 최종 전체 일정은 Processor가 견적 제출 시 작성하는 `leadDays`로 확정한다.
- 결과 없음은 HTTP200, 빈 items, total0이다. UI에서 조건 완화 안내를 보여준다.
- 조회는 재고를 예약하지 않는다. 실제 요청 생성 때 업체 조건과 가용량을 다시 검증한다.

## 내 가공 가능 소재

`GET /processing-requests/materials` (`/api/v1/processing-requests/materials`)

활성 BRAND의 보유 소재만 반환한다. data는 `materialId, materialCode, name, materialType, quantity, lockedQuantity, availableQuantity` 배열이다. 보유량이 양수인 ACTIVE 소재를 포함하고, 예약량은 모든 ACTIVE Lock의 합계다. 사용 가능량이 0인 소재도 보유/예약 현황 확인을 위해 포함한다.

신규 DB는 `schema.sql`을 사용한다. 기존 DB에는 `src/main/resources/db/migrations/2026-09-10-processor-process-lead-times.sql`을 수동 적용해야 한다. 기존 전역 소요일 구간은 해당 업체의 각 소재·공정 조합으로 복제된다.

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

시드: 폐데님30000g 중5000g 예약, 면15000g, 가공업체3곳. 세척·해체·재단 검색은 결과가 있고 염색은 결과가 없다. 요청을 만들면 예약량이 증가하고 취소하면 복원된다. DB는 재시작하면 초기화된다. 실시간 블록체인 동기화·견적 확정·온체인 거래는 이 테스트에 포함하지 않는다.
