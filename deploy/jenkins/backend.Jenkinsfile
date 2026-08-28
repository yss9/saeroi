// Backend CI/CD
// - 트리거: backend/master push (GitLab webhook)
// - Job 설정: Pipeline script from SCM → 브랜치 deploy/master → Script Path jenkins/backend.Jenkinsfile
// - TODO: REPO_URL 을 실제 GitLab 리포 주소로 교체
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        REPO_URL   = 'https://lab.ssafy.com/<GROUP>/S15P21E104.git' // TODO
        IMAGE      = 'circular-fashion/backend'
        DEPLOY_DIR = '/home/ubuntu/deploy' // deploy/master 가 clone 된 경로
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'backend/master', credentialsId: 'gitlab-token', url: env.REPO_URL
            }
        }
        stage('Test') {
            steps {
                sh 'chmod +x gradlew && ./gradlew test'
            }
        }
        stage('Build') {
            steps {
                sh './gradlew bootJar -x test'
            }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE}:latest -t ${IMAGE}:${BUILD_NUMBER} ."
            }
        }
        stage('Deploy') {
            steps {
                sh "bash ${DEPLOY_DIR}/scripts/deploy-backend.sh"
            }
        }
    }

    post {
        success { echo "backend 배포 성공: ${IMAGE}:${BUILD_NUMBER}" }
        failure { echo 'backend 파이프라인 실패 — 로그 확인 필요' }
    }
}
