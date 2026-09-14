// Frontend CI/CD (Jenkins Multibranch Pipeline)
// - CI : frontend/develop, frontend/feat/* push → install + lint + (test 있으면) + build
// - CD : frontend/master push                   → 이미지 빌드(VITE_* 주입) + EC2 배포
//
// 이 파일은 frontend 파트가 소유한다. 스테이지 추가/수정 자유. 계약은 둘뿐:
//   1) 이미지 이름 circular-fashion/frontend:latest
//   2) Deploy 스테이지는 when { branch 'frontend/master' }
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        IMAGE            = 'circular-fashion/frontend'
        DEPLOY_DIR       = '/home/ubuntu/deploy'
        npm_config_cache = '/var/jenkins_home/.npm'
    }

    stages {
        // 빌드 결과를 GitLab 커밋 상태로 보고한다 → GitLab 화면에 초록 체크/빨간 X 가 뜬다.
        // 스크립트는 deploy/master 소유이고 보고 실패는 빌드에 영향을 주지 않는다(항상 exit 0).
        stage('GitLab 상태 보고') {
            steps { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh running' }
        }

        stage('Install') {
            steps { sh 'npm ci --no-audit --no-fund' }
        }

        stage('Lint') {
            steps { sh 'npm run lint' }
        }

        stage('Test') {
            // package.json 에 test 스크립트가 아직 없어 조용히 건너뛴다.
            // vitest 를 붙이고 "test": "vitest run" 만 추가하면 파이프라인 수정 없이 자동 실행된다.
            // 주의: `npm test --if-present` 가 아니라 `npm run test --if-present` 여야 동작한다.
            steps { sh 'npm run test --if-present' }
        }

        stage('Build') {
            // tsc -b 타입 체크 겸용. develop/feat 에서도 돌려 빌드 깨짐을 즉시 잡는다.
            steps { sh 'npm run build' }
        }

        // 상시 통합 테스트 스택(/test) 배포. frontend/develop 병합마다 이미지를 :test 태그로
        // 새로 빌드해 deploy 파트의 scripts/test-deploy.sh 로 넘긴다.
        //
        // VITE_API_BASE_URL 을 오버라이드하고 VITE_BASE_PATH 를 추가로 넘긴다 — 나머지
        // VITE_*(Besu/컨트랙트 주소)는 운영 .env 값을 그대로 재사용한다(같은 테스트넷).
        // 상대경로 '/api' 그대로 두면 /test/ 에서 서빙된 번들이 운영 backend(/api)를 호출해
        // 버리므로 반드시 /test/api 로 다시 빌드한다. VITE_BASE_PATH 는 vite.config.ts 의
        // `base` 로 들어가 자산 경로와 React Router basename 을 함께 맞춘다.
        stage('Test Deploy') {
            when { branch 'frontend/develop' }
            steps {
                sh '''
                    set -eu
                    ENV_FILE="$DEPLOY_DIR/.env"
                    [ -f "$ENV_FILE" ] || { echo "FAIL: $ENV_FILE 없음"; exit 1; }

                    set --
                    while IFS= read -r line; do
                        case "$line" in
                            VITE_API_BASE_URL=*) line="VITE_API_BASE_URL=/test/api" ;;
                        esac
                        [ -n "$line" ] && set -- "$@" --build-arg "$line"
                    done <<EOF
$(grep -E '^VITE_[A-Za-z0-9_]+=' "$ENV_FILE")
EOF
                    set -- "$@" --build-arg "VITE_BASE_PATH=/test/"

                    # .env 의 VITE_ 키가 Dockerfile 에 ARG 로 선언돼 있는지 확인한다.
                    # 선언이 없으면 docker 는 "build-args were not consumed" 경고만 내고 값을 버린다.
                    # 빌드는 성공하고 번들에는 빈 문자열이 들어가서, "값을 안 채웠을 때"와 증상이
                    # 똑같아 구분이 안 된다. master 의 Docker Build 스테이지에만 이 검사가 있어서
                    # test 스택은 조용히 느려지는 채로 넘어간 적이 있다(VITE_CHAIN_FROM_BLOCK).
                    MISSING=""
                    for KEY in $(grep -oE '^VITE_[A-Za-z0-9_]+' "$ENV_FILE"); do
                        grep -qE "^ARG[[:space:]]+$KEY([[:space:]]|=|$)" docker/Dockerfile || MISSING="$MISSING $KEY"
                    done
                    if [ -n "$MISSING" ]; then
                        echo "FAIL: docker/Dockerfile 에 ARG 선언이 없는 키:$MISSING"
                        echo "      선언이 없으면 값이 번들에 들어가지 않는다. Dockerfile 에 ARG 를 추가할 것."
                        exit 1
                    fi

                    # :test 하나만 찍으면 다음 빌드가 그 태그를 새 이미지로 옮기는 순간
                    # 지금 뜬 이미지가 태그를 전부 잃어 test-deploy.sh 가 롤백 대상을 못 찾는다.
                    docker build -f docker/Dockerfile "$@" -t "$IMAGE:test" -t "$IMAGE:test-$BUILD_NUMBER" .
                '''
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/test-deploy.sh frontend'
            }
        }

        stage('Docker Build') {
            when { branch 'frontend/master' }
            steps {
                // VITE_* 는 번들에 그대로 박혀 브라우저로 나가는 "공개 값"이라 Credentials 로 감싸지 않는다.
                // .env 를 통째로 source 하면 MYSQL_ROOT_PASSWORD·JWT_SECRET 까지 빌드 환경에 올라
                // 로그로 샐 표면이 생기므로, VITE_ 로 시작하는 줄만 골라 build-arg 로 넘긴다.
                // 키 목록은 .env 가 정하므로 키를 추가해도 이 파일은 고칠 필요가 없다.
                // 다만 Dockerfile 의 ARG 선언은 반드시 따라와야 해서, 그 어긋남만 아래에서 검사한다.
                sh '''
                    set -eu
                    ENV_FILE="$DEPLOY_DIR/.env"
                    [ -f "$ENV_FILE" ] || { echo "FAIL: $ENV_FILE 없음 — jenkins 서비스에 deploy 디렉터리가 마운트됐는지 확인"; exit 1; }

                    set --
                    while IFS= read -r line; do
                        [ -n "$line" ] && set -- "$@" --build-arg "$line"
                    done <<EOF
$(grep -E '^VITE_[A-Za-z0-9_]+=' "$ENV_FILE")
EOF
                    [ "$#" -gt 0 ] || { echo "FAIL: .env 에 VITE_* 값이 하나도 없음"; exit 1; }

                    # .env 의 VITE_ 키가 Dockerfile 에 ARG 로 선언돼 있는지 확인한다.
                    # 선언이 없으면 docker 는 "build-args were not consumed" 경고만 내고 값을 버린다.
                    # 빌드는 성공하고 번들에는 빈 문자열이 들어가서, "값을 안 채웠을 때"와 증상이
                    # 똑같아 구분이 안 된다. 조용히 빈 값으로 배포하느니 여기서 세운다.
                    MISSING=""
                    for KEY in $(grep -oE '^VITE_[A-Za-z0-9_]+' "$ENV_FILE"); do
                        grep -qE "^ARG[[:space:]]+$KEY([[:space:]]|=|$)" docker/Dockerfile || MISSING="$MISSING $KEY"
                    done
                    if [ -n "$MISSING" ]; then
                        echo "FAIL: docker/Dockerfile 에 ARG 선언이 없는 키:$MISSING"
                        echo "      선언이 없으면 값이 번들에 들어가지 않는다. Dockerfile 에 ARG 를 추가할 것."
                        exit 1
                    fi

                    echo "주입 키: $(grep -oE '^VITE_[A-Za-z0-9_]+' "$ENV_FILE" | tr '\n' ' ')"
                    docker build -f docker/Dockerfile "$@" -t "$IMAGE:latest" -t "$IMAGE:$BUILD_NUMBER" .
                '''
            }
        }

        stage('Deploy') {
            when { branch 'frontend/master' }
            steps {
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/deploy-frontend.sh'
            }
        }
    }

    post {
        success {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh success'
            echo "frontend OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        failure {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh failed'
            echo "frontend FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        aborted { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh canceled' }
        // cleanup 은 선언적 파이프라인에서 항상 마지막이라 위 sh 들이 먼저 돈다
        cleanup { deleteDir() }
    }
}
