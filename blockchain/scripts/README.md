# Scripts

스크립트는 목적별로 분리한다.

- `deploy/`: 전체 또는 개별 컨트랙트 배포. `deployments/<network>.json`을 단일 주소 원본으로 사용한다.
- `admin/`: 역할 부여와 운영 트랜잭션처럼 상태를 바꾸는 작업.
- `verify/`: 배포 결과와 컨트랙트 연결 상태 검증.
- `inspect/`: RPC, 영수증, 이벤트 등 읽기 전용 조회.
- `build/`: ABI 같은 빌드 산출물 생성.
- `dev/`: 로컬 데모 및 개발 지갑 생성.
- `config/`: 커밋 가능한 예시 설정. 실제 주소 목록은 `.gitignore` 대상이다.
- `lib/`: 공통 경로 등 스크립트 공용 코드.

## ProcessingRegistry 단독 재배포

PowerShell에서 다음 순서로 실행한다.

```powershell
$env:ALLOW_PROCESSING_REDEPLOY="true"
npm run deploy:processing:ssafy
npm run roles:ssafy
```

단독 재배포 스크립트는 기존 `MaterialToken`을 재사용하고, 새 레지스트리에 필요한 소재 잠금·워크플로 역할을 자동 부여한다. 이전 배포 파일은 `deployments/history/`에 보관하고 현재 주소만 `deployments/ssafy.json`에 반영한다. 사용자 `BRAND`/`PROCESSOR` 역할과 백엔드·프론트엔드 주소 설정은 별도로 새 주소에 맞춰야 한다.

