// Blockchain CI — 배포 없음.
// 운영 컨트랙트는 push마다 자동 배포하지 않는다. blockchain/master 의 Besu 배포는
// 수동 승인 후 담당자(1번)가 직접 수행하고 ABI/주소를 갱신한다. (README 12장)
//
// 이 파일은 blockchain 파트가 소유한다. 빌드 스텝은 자유롭게 수정해도 된다.
// Jenkins Multibranch Pipeline 이 이 브랜치를 자동 체크아웃하므로 Checkout 스테이지가 없다.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // npm 캐시를 영속 볼륨에 두어 컨테이너를 재생성해도 유지되게 한다
        npm_config_cache = '/var/jenkins_home/.npm'
    }

    stages {
        stage('Install') {
            steps { sh 'npm ci --no-audit --no-fund' }
        }

        stage('Compile') {
            steps { sh 'npm run compile' }
        }

        stage('Test') {
            // hardhat 인메모리 네트워크(chainId 31337)로 돌기 때문에
            // SSAFY_RPC_URL / DEPLOYER_PRIVATE_KEY 없이도 통과해야 한다.
            steps { sh 'npm test' }
        }
    }

    post {
        success { echo "blockchain OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        failure { echo "blockchain FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        cleanup { deleteDir() }
    }
}
