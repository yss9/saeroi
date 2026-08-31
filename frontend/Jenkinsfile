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

        stage('Docker Build') {
            when { branch 'frontend/master' }
            steps {
                // VITE_* 는 번들에 그대로 박혀 브라우저로 나가는 "공개 값"이라 Credentials 로 감싸지 않는다.
                // .env 를 통째로 source 하면 MYSQL_ROOT_PASSWORD·JWT_SECRET 까지 빌드 환경에 올라
                // 로그로 샐 표면이 생기므로, VITE_ 로 시작하는 줄만 골라 build-arg 로 넘긴다.
                // 키 목록을 .env 가 스스로 정하므로 VITE 키를 추가해도 이 파일을 고칠 필요가 없다.
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
        success { echo "frontend OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        failure { echo "frontend FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        cleanup { deleteDir() }
    }
}
