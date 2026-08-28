// AI Server CI/CD
// - 트리거: ai/master push (GitLab webhook)
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        REPO_URL   = 'https://lab.ssafy.com/<GROUP>/S15P21E104.git' // TODO
        IMAGE      = 'circular-fashion/ai'
        DEPLOY_DIR = '/home/ubuntu/deploy'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'ai/master', credentialsId: 'gitlab-token', url: env.REPO_URL
            }
        }
        stage('Test') {
            steps {
                sh '''
                    python3 -m venv .venv
                    . .venv/bin/activate
                    pip install -r requirements.txt
                    pytest tests --maxfail=1 || [ ! -d tests ]
                '''
            }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE}:latest -t ${IMAGE}:${BUILD_NUMBER} ."
            }
        }
        stage('Deploy') {
            steps {
                sh "bash ${DEPLOY_DIR}/scripts/deploy-ai.sh"
            }
        }
    }

    post {
        success { echo "ai 배포 성공: ${IMAGE}:${BUILD_NUMBER}" }
        failure { echo 'ai 파이프라인 실패 — 로그 확인 필요' }
    }
}
