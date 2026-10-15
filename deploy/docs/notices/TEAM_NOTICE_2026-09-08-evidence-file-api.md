# [공지] 증빙 파일 조회·삭제 API 추가 및 응답 필드 변경 (2026-09-08)

작성: 6번(인프라 · Material/공통) · 대상: 프론트엔드, 증빙을 붙이는 모든 파트

관련 티켓: `S15P21E104-162`, `S15P21E104-249`
`backend/master` #12 로 **운영에 이미 배포됐습니다** (2026-09-08 11:50, 헬스체크 통과).

## 1. 무엇이 바뀌었나

증빙 파일을 **되돌려주는 경로가 없었습니다.** 업로드는 되는데 올린 파일을 다시 볼 수 없었습니다.
조회 엔드포인트와 삭제 엔드포인트를 추가했고, 그에 맞춰 응답 필드가 늘었습니다.

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 파일 조회 | **없음** | `GET /api/evidence/{evidenceFileId}/content` |
| 파일 삭제 | **없음** | `DELETE /api/evidence/{evidenceFileId}` |
| 응답 필드 | 6개 | **8개** (`contentPath`, `deletedAt` 추가) |

**기존 필드는 하나도 지우지 않았습니다.** 지금 동작하는 화면은 깨지지 않습니다.

## 2. 응답 필드 — `contentPath` 를 쓰세요

`POST /api/evidence`, `GET /api/evidence`, `DELETE /api/evidence/{id}` 가 공통으로 쓰는 응답입니다.

| 필드 | 상태 | 설명 |
| --- | --- | --- |
| `evidenceFileId` | 그대로 | |
| `storageKey` | 그대로 | 저장 키. **URL 이 아닙니다** — 이 값으로는 파일을 받을 수 없습니다 |
| `contentPath` | **추가** | 파일을 받을 경로. 예: `/api/evidence/12/content` |
| `fileHash` | 그대로 | `0x` + 64자 (66자). 온체인에 기록되는 값 |
| `evidenceType` | 그대로 | |
| `uploadedBy` | 그대로 | |
| `uploadedAt` | 그대로 | |
| `deletedAt` | **추가** | 삭제된 증빙이면 시각, 아니면 `null` |

`storageKey` 에 절대 URL 을 넣지 않는 것은 의도된 설계입니다 — 도메인이나 버킷이 바뀔 때
저장된 전 행을 고쳐야 하기 때문입니다. 그래서 조회 경로를 별도 필드로 내려줍니다.

## 3. 파일 조회 — presigned URL 이 아니라 우리 서버를 거칩니다

`GET /api/evidence/{evidenceFileId}/content`

- 인증 필요 (JWT)
- **이 엔드포인트만 공통 `ApiResponse` 봉투가 아닙니다.** 본문이 파일 바이트입니다.
  실패할 때는 평소처럼 봉투로 옵니다.
- 응답 헤더: `Content-Disposition: inline` (파일명 없음), `X-Content-Type-Options: nosniff`,
  `Cache-Control: private, no-store`
- `Content-Type` 은 저장 키의 확장자에서 파생합니다. 업로드 때 클라이언트가 선언한 값을
  그대로 믿지 않습니다.

**S3/MinIO presigned URL 을 쓰지 않은 이유**: 발급된 링크에는 인가가 없어서 만료 전까지
문자열을 가진 누구에게든 통합니다. 증빙은 남의 소재 자료이고 계근표에는 거래 상대·중량·단가가
찍혀 있어, 링크가 채팅이나 리퍼러로 새면 그대로 노출됩니다. 또 `STORAGE_TYPE=local` 배포에는
presigned 에 대응하는 것이 없어 조회 경로가 통째로 사라집니다.

## 4. 파일 삭제 — 권한이 조회보다 좁습니다

`DELETE /api/evidence/{evidenceFileId}`

| | 업로더 | 소재 등록자 | 소재 현재 보유자 |
| --- | --- | --- | --- |
| 조회 | O | O | **O** |
| 삭제 | O | O | **X** |

- **보유자에게 조회를 허용한 이유**: 받은 소재의 증빙을 볼 수 없으면 추적성이 무의미합니다.
- **보유자에게 삭제를 막은 이유**: 이력을 지우고 되파는 경로를 막습니다.
- 역할(`SUPPLIER`/`BRAND`/…)로 판정하지 않습니다. 관계로 판정합니다.

성공하면 `deletedAt` 이 채워진 응답이 옵니다. 행은 남고(소프트 삭제) 저장소의 파일 바이트는
실제로 지웁니다.

## 5. 온체인에 고정된 증빙은 아무도 지울 수 없습니다

대상(소재)에 `evidence_hash` 또는 `token_id` 가 있으면 **그 대상의 증빙 전부**가 잠깁니다.
`409 EVIDENCE_ANCHORED_ON_CHAIN` 이 옵니다.

파일 단위가 아니라 **대상 단위**로 판정합니다. 체인에는 manifest 해시 하나만 올라가서
어느 파일이 그 안에 들어갔는지 행만 보고는 알 수 없기 때문입니다. 모호하면 막는 쪽으로 두었습니다.

> 프론트에서는 온체인 등록된 소재의 증빙에 **삭제 버튼을 아예 노출하지 않는 편**이 좋습니다.
> 눌러서 409 를 받는 것보다 낫습니다.

## 6. 에러 코드

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `EVIDENCE_NOT_FOUND` | 404 | 증빙이 없거나 이미 삭제됨 |
| `EVIDENCE_ACCESS_FORBIDDEN` | 403 | 조회 권한 없음 |
| `EVIDENCE_DELETE_FORBIDDEN` | 403 | 삭제 권한 없음 (업로더·등록자가 아님) |
| `EVIDENCE_ANCHORED_ON_CHAIN` | 409 | 온체인 고정된 증빙 |
| `EVIDENCE_REFERENCE_NOT_SUPPORTED` | 400 | 확인할 수 없는 참조 타입 |

권한을 온체인 판정보다 **먼저** 봅니다. 그래야 403/409 차이로 "이 소재가 체인에 올라갔는지"가
권한 없는 사람에게 새지 않습니다.

## 7. DB · 배포 (인프라에서 처리 완료)

`evidence_file` 에 `deleted_at`, `deleted_by` 컬럼이 추가됐습니다. 운영은 `ddl-auto: validate` 라
DB 에 컬럼이 없으면 기동 자체가 실패합니다.

- **운영 DB ALTER 는 2026-09-08 에 적용 완료**했습니다 (백업 후, 대상 0행).
- 배포 전 `bash scripts/check-schema.sh` 로 컬럼과 **타입**까지 대조해 `OK` 를 확인했습니다.
- **로컬 DB 를 쓰시는 분은 `schema.sql` 을 다시 반영해 주세요.**

## 8. 각 파트가 할 일

**프론트엔드**

1. 증빙 파일을 보여주는 자리에서 `storageKey` 대신 **`contentPath`** 를 쓰세요.
   현재 `frontend/develop` 은 `storageKey` 를 URL 로 쓰지 않고 안내 문구를 표시하고 있어
   **깨지지는 않습니다.** 이제 미리보기를 만들 수 있게 됐다는 뜻입니다.
2. 온체인 고정 소재의 증빙에는 삭제 버튼을 노출하지 마세요 (5번 참고).
3. 조회·삭제 모두 JWT 가 필요하고, 조회 응답만 `ApiResponse` 봉투가 아닙니다 (3번 참고).

**다른 도메인 (Product 이미지, 분쟁 첨부 등)**

도메인 중립 `POST /api/files` 는 만들지 않았습니다. `reference_id` 에 FK 를 걸 수 없어
**대상 존재 확인과 인가를 할 수 없고**, 없는 대상을 가리키는 행의 해시가 체인에 올라갈 수 있습니다.
같은 `FileStorageService` 위에 **각 도메인 엔드포인트**를 두는 방향을 권합니다.
필요하시면 말씀해 주세요 — 저장·해시·검증은 이미 공통이라 도메인 쪽은 얇게 붙습니다.

**블록체인**

증빙 삭제 차단이 `material.evidence_hash` 와 `token_id` 의 존재 여부를 봅니다.
온체인 기록 방식이 바뀌면 이 판정도 함께 바뀌어야 하니 미리 알려 주세요.

## 9. 아직 하지 않은 것

- **MinIO 전환은 하지 않았습니다.** `STORAGE_TYPE` 이 없어 backend 는 여전히 파일시스템
  (`local`)을 씁니다. MinIO 컨테이너와 `evidence` 버킷은 준비돼 있고, backend ↔ MinIO 왕복과
  해시 일치도 실측 확인했습니다. 전환 시점은 따로 공지하겠습니다.
- 증빙 미리보기 UI 는 이 변경 범위가 아닙니다 (프론트 티켓 필요).
