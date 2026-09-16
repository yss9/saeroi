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
        // 빌드 결과를 GitLab 커밋 상태로 보고한다 → GitLab 화면에 초록 체크/빨간 X 가 뜬다.
        // 스크립트는 deploy/master 소유이고 보고 실패는 빌드에 영향을 주지 않는다(항상 exit 0).
        stage('GitLab 상태 보고') {
            steps { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh running' }
        }

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

        // 마이그레이션이 schema.sql 이 선언한 목표 상태를 실제로 재현하는지 병합 전에
        // 검증한다. db/migrations/*.sql 은 사람이 schema.sql 과 같이 손으로 맞춰 쓰는
        // 파일이라 어긋날 수 있고, 지금까지는 그 어긋남이 배포 시점 Hibernate validate
        // 크래시루프로만 드러났다 (deploy/master:docs/SCHEMA_INCIDENTS.md 2026-09-16 —
        // 이 스테이지가 실제로 잡아낸 사고: status ENUM 이 CANCELED 값을 빠뜨린 채 병합됨).
        // backend/master 는 develop 을 거쳐 이미 검증된 커밋만 받으므로 스킵한다.
        stage('마이그레이션 스키마 대조') {
            when { not { branch 'backend/master' } }
            steps {
                // Jenkins 의 GitLab 인증(gitlab-token)은 자신의 체크아웃 스텝에만 한시적으로
                // 걸리고 이후 shell 스텝의 git 명령까지 이어지지 않는다 — verify-migrations.sh가
                // 베이스라인 브랜치를 직접 fetch 하려면 자격증명을 명시적으로 실어줘야 한다.
                withCredentials([usernamePassword(credentialsId: 'gitlab-token', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                    sh '''
                        git config --local credential.helper "!f() { echo username=$GIT_USER; echo password=$GIT_PASS; }; f"
                        bash scripts/verify-migrations.sh
                    '''
                }
            }
        }

        // 상시 통합 테스트 스택(/test) 배포. backend/develop 병합마다 이미지를 :test 태그로
        // 새로 빌드해 deploy 파트의 scripts/test-deploy.sh 로 넘긴다 — 운영(:latest, backend/master)
        // 과는 완전히 분리된 태그·컨테이너(cft-backend)라 서로 섞이지 않는다.
        // deploy-service.sh 계열과 같은 flock 락을 공유해 EC2 단일 Docker 데몬 위에서의
        // 동시 배포/빌드 경합을 피한다 (2026-09-07 실사고 재발 방지).
        stage('Test Deploy') {
            when { branch 'backend/develop' }
            steps {
                sh 'docker build -f docker/Dockerfile -t $IMAGE:test .'
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/test-deploy.sh backend'
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
        success {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh success'
            echo "backend OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        failure {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh failed'
            echo "backend FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        aborted { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh canceled' }
        // cleanup 은 선언적 파이프라인에서 항상 마지막이라 위 sh 들이 먼저 돈다
        cleanup { deleteDir() }
    }
}
