// Backend CI/CD (Jenkins Multibranch Pipeline)
// - CI : backend/develop, backend/feat/* push → 테스트만
// - CD : backend/master push                  → 이미지 빌드 + EC2 배포
//
// 이 파일은 backend 파트가 소유한다. 스테이지 추가/수정 자유. 지켜야 할 계약은 둘뿐:
//   1) 이미지 이름 circular-fashion/backend:latest
//   2) Deploy 스테이지는 when { branch 'backend/master' }
// Multibranch가 이 브랜치를 자동 체크아웃하므로 Checkout 스테이지가 없다.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        IMAGE      = 'circular-fashion/backend'
        DEPLOY_DIR = '/home/ubuntu/deploy'
        // Maven 로컬 저장소를 영속 볼륨에 두어 매 빌드 재다운로드를 피한다
        MAVEN_ARGS = '-B -ntp -Dmaven.repo.local=/var/jenkins_home/.m2/repository'
    }

    stages {
        stage('Test') {
            steps {
                sh '''
                    chmod +x mvnw
                    ./mvnw $MAVEN_ARGS test
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            // CI 브랜치에서는 이미지를 만들지 않는다 — 브랜치명에 슬래시가 있어 도커 태그로 쓸 수 없고,
            // feat 브랜치 이미지가 EC2 디스크를 먹으며 latest 오염 위험도 있다.
            when { branch 'backend/master' }
            steps {
                // jar 패키징은 Dockerfile 1st stage 에서 수행한다 (빌드 환경을 Java 21 로 고정)
                sh 'docker build -f docker/Dockerfile -t $IMAGE:latest -t $IMAGE:$BUILD_NUMBER .'
            }
        }

        stage('Deploy') {
            when { branch 'backend/master' }
            steps {
                // EC2 1대에서 파트 간 compose 동시 실행을 직렬화한다
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/deploy-backend.sh'
            }
        }
    }

    post {
        success { echo "backend OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        failure { echo "backend FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        cleanup { deleteDir() }
    }
}
