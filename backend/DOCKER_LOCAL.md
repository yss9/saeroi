# 로컬 통합 실행

`S15P21E104_back`과 `S15P21E104_front`가 같은 상위 폴더에 있을 때 MySQL, 백엔드,
프론트엔드를 Docker Compose로 함께 실행할 수 있다.

```text
S15P21E104/
├─ S15P21E104_back/
└─ S15P21E104_front/
```

## 실행

백엔드 폴더에서 다음 명령을 실행한다.

```powershell
docker compose -f docker-compose.local.yml up --build
```

- 프론트엔드: <http://localhost:5173>
- 백엔드 상태 확인: <http://localhost:8080/api/health>
- 브라우저의 `/api` 요청은 프론트 nginx가 Compose 내부의 백엔드로 전달한다.
- MySQL은 Compose 네트워크 안에서만 열리므로 기존 로컬 MySQL의 3306 포트와 충돌하지 않는다.

백그라운드 실행은 `-d`를 추가한다.

```powershell
docker compose -f docker-compose.local.yml up --build -d
```

## 종료

```powershell
docker compose -f docker-compose.local.yml down
```

DB와 업로드 파일은 Docker 볼륨에 유지된다. DB까지 완전히 초기화해야 할 때만 아래 명령을
사용한다. 이 명령은 로컬 Compose DB와 업로드 파일을 삭제한다.

```powershell
docker compose -f docker-compose.local.yml down -v
```

## 환경변수

기본값은 로컬 개발 전용이며 실제 결제와 블록체인 연동은 비활성 상태로 실행된다. 필요한 경우
`S15P21E104_back/.env`에 다음 값을 설정한다.

```dotenv
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_DATABASE=upcycle
MYSQL_USER=upcycle
MYSQL_PASSWORD=upcyclepassword
JWT_SECRET=dXBjeWNsZS1sb2NhbC1jb21wb3NlLWp3dC1zZWNyZXQtMjAyNg==
JWT_ACCESS_TOKEN_TTL=PT30M
JWT_REFRESH_TOKEN_TTL=P14D

KAKAOPAY_SECRET_KEY=
KAKAOPAY_CID=TC0ONETIME

BESU_CHAIN_ID=31221
MATERIAL_TOKEN_ADDRESS=
MARKETPLACE_ADDRESS=

VITE_BESU_RPC_URL=
VITE_BESU_CHAIN_ID=31221
VITE_BESU_EXPLORER_URL=
VITE_MATERIAL_CONTRACT_ADDRESS=
VITE_MARKETPLACE_CONTRACT_ADDRESS=
VITE_PROCESSING_CONTRACT_ADDRESS=
VITE_PRODUCTION_CONTRACT_ADDRESS=
```

프론트의 `VITE_*` 값은 이미지 빌드 때 들어간다. 값을 변경한 뒤에는 `up --build`로
프론트 이미지를 다시 빌드해야 한다.

## 기존 DB 스키마 갱신

`schema.sql`은 MySQL 볼륨이 처음 생성될 때만 자동 실행된다. 이전 Compose 볼륨을 계속
사용하면서 새 마이그레이션이 생겼다면 해당 SQL을 직접 적용한다. 개발 데이터를 버려도 된다면
`down -v` 후 다시 실행하는 것이 가장 단순하다.
