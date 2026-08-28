// Blockchain CI (배포 없음)
// - 트리거: blockchain/develop push (GitLab webhook)
// - README 12장: 운영 컨트랙트는 push마다 자동 배포하지 않는다.
//   blockchain/master 의 Besu 배포는 수동 승인 후 담당자(1번)가 직접 수행하고,
//   생성된 ABI / Contract Address 를 blockchain/master 에 갱신한다.
pipeline {
    agent any

    options {
        timestamps()
    }

    environment {
        REPO_URL = 'https://lab.ssafy.com/<GROUP>/S15P21E104.git' // TODO
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'blockchain/develop', credentialsId: 'gitlab-token', url: env.REPO_URL
            }
        }
        stage('Compile') {
            steps {
                sh 'npm ci'
                sh 'npx hardhat compile'
            }
        }
        stage('Test') {
            steps {
                sh 'npx hardhat test'
            }
        }
    }

    post {
        success { echo 'blockchain compile/test 통과' }
        failure { echo 'blockchain 파이프라인 실패 — 로그 확인 필요' }
    }
}
