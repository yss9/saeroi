// Frontend CI/CD
// - 트리거: frontend/master push (GitLab webhook)
// - VITE_* 값은 빌드 시점에 주입되므로 build-arg 로 전달한다. (frontend Dockerfile에서 ARG 선언 필요)
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        REPO_URL   = 'https://lab.ssafy.com/s15-blockchain-pred-sub1/S15P21E104.git'
        IMAGE      = 'circular-fashion/frontend'
        DEPLOY_DIR = '/home/ubuntu/deploy'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'frontend/master', credentialsId: 'gitlab-token', url: env.REPO_URL
            }
        }
        stage('Install & Test') {
            steps {
                sh 'npm ci'
                sh 'npm test --if-present'
            }
        }
        stage('Docker Build') {
            steps {
                // Vite 빌드는 Dockerfile 내부에서 수행. VITE_* 는 EC2의 .env 값을 읽어 전달.
                sh """
                    set -a && . ${DEPLOY_DIR}/.env && set +a
                    docker build \
                      --build-arg VITE_API_BASE_URL=\$VITE_API_BASE_URL \
                      --build-arg VITE_BESU_RPC_URL=\$VITE_BESU_RPC_URL \
                      --build-arg VITE_BESU_CHAIN_ID=\$VITE_BESU_CHAIN_ID \
                      --build-arg VITE_MATERIAL_TOKEN_ADDRESS=\$VITE_MATERIAL_TOKEN_ADDRESS \
                      --build-arg VITE_MARKETPLACE_ADDRESS=\$VITE_MARKETPLACE_ADDRESS \
                      --build-arg VITE_ESCROW_ADDRESS=\$VITE_ESCROW_ADDRESS \
                      -t ${IMAGE}:latest -t ${IMAGE}:${BUILD_NUMBER} .
                """
            }
        }
        stage('Deploy') {
            steps {
                sh "bash ${DEPLOY_DIR}/scripts/deploy-frontend.sh"
            }
        }
    }

    post {
        success { echo "frontend 배포 성공: ${IMAGE}:${BUILD_NUMBER}" }
        failure { echo 'frontend 파이프라인 실패 — 로그 확인 필요' }
    }
}
