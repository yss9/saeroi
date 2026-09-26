// AI Server CI/CD (Jenkins Multibranch Pipeline)
//
// 이 브랜치에는 아직 소스가 없다. 소스가 없으면 "실패"가 아니라 "스킵"이다.
// 파일만 추가하면 파이프라인이 알아서 켜지므로 인프라에 따로 요청할 것이 없다:
//   requirements.txt 또는 pyproject.toml 추가 → Test 스테이지 자동 활성화
//   tests/ 디렉터리 추가                       → pytest 자동 실행
//   위 + docker/Dockerfile + ai/master push    → 이미지 빌드 + EC2 자동 배포
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        IMAGE      = 'circular-fashion/ai'
        DEPLOY_DIR = '/home/ubuntu/deploy'
    }

    stages {
        // 빌드 결과를 GitLab 커밋 상태로 보고한다 → GitLab 화면에 초록 체크/빨간 X 가 뜬다.
        // 스크립트는 deploy/master 소유이고 보고 실패는 빌드에 영향을 주지 않는다(항상 exit 0).
        stage('GitLab 상태 보고') {
            steps { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh running' }
        }

        stage('Detect') {
            steps {
                script {
                    env.HAS_DEPS       = (fileExists('requirements.txt') || fileExists('pyproject.toml')) ? 'true' : 'false'
                    env.HAS_TESTS      = (fileExists('tests') || fileExists('test')) ? 'true' : 'false'
                    env.HAS_DOCKERFILE = fileExists('docker/Dockerfile') ? 'true' : 'false'
                    echo "deps=${env.HAS_DEPS} tests=${env.HAS_TESTS} dockerfile=${env.HAS_DOCKERFILE}"
                    if (env.HAS_DEPS != 'true') {
                        echo 'SKIP: 파이썬 소스가 아직 없습니다. requirements.txt 를 추가하면 자동으로 CI가 켜집니다.'
                    }
                }
            }
        }

        stage('Test') {
            when { environment name: 'HAS_DEPS', value: 'true' }
            steps {
                sh '''
                    set -eu
                    # 에이전트에 python3.12 가 없다. rapidocr-onnxruntime 이 3.13+ 을 지원하지 않으므로
                    # docker/Dockerfile 과 동일한 python:3.12-slim 컨테이너 안에서 테스트를 돌린다.
                    # Jenkins 는 cf-jenkins 컨테이너에서 돌고 docker.sock 으로 호스트 데몬에 명령을 보낸다.
                    # -v 의 왼쪽 경로는 호스트 기준으로 해석되는데 jenkins_home 이 named volume 이라
                    # 호스트에는 /var/jenkins_home/workspace/... 가 없다. Docker 가 빈 디렉터리를 만들어
                    # 마운트해버려서 /workspace 가 비었고 docker/test.sh 를 못 찾았다.
                    # --volumes-from 으로 cf-jenkins 의 볼륨을 물려받아 $PWD 를 그대로 쓴다.
                    docker run --rm \
                        -u "$(id -u):$(id -g)" \
                        -e HAS_TESTS="$HAS_TESTS" \
                        --volumes-from cf-jenkins -w "$PWD" \
                        python:3.12-slim \
                        bash docker/test.sh
                '''
            }
        }

        // 상시 통합 테스트 스택(/test) 배포. ai/develop 병합마다 이미지를 :test 태그로 새로
        // 빌드해 deploy 파트의 scripts/test-deploy.sh 로 넘긴다. 소스가 아직 없으면(HAS_DEPS/
        // HAS_DOCKERFILE = false) 위 Docker Build/Deploy 와 같은 이유로 조용히 건너뛴다.
        stage('Test Deploy') {
            when {
                allOf {
                    branch 'ai/develop'
                    environment name: 'HAS_DEPS',       value: 'true'
                    environment name: 'HAS_DOCKERFILE', value: 'true'
                }
            }
            steps {
                // :test 하나만 찍으면 다음 빌드가 그 태그를 새 이미지로 옮기는 순간 지금 뜬 이미지가 태그를 전부
                // 잃어, test-deploy.sh 의 롤백 판정("circular-fashion/<svc>:" 태그가 남은 이미지)에서 빠진다 —
                // 실제로 backend/develop #137 까지 매 test 배포가 "롤백 대상 없음" 이었다 (2026-09-18 확인).
                // frontend Jenkinsfile 과 같은 방식으로 :test-$BUILD_NUMBER 를 함께 찍는다. 쌓이는 번호 태그는
                // deploy/master 의 scripts/prune-images.sh 크론이 서비스별 최신 5개만 남기고 정리한다.
                sh 'docker build -f docker/Dockerfile -t $IMAGE:test -t $IMAGE:test-$BUILD_NUMBER .'
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/test-deploy.sh ai'
            }
        }

        stage('Docker Build') {
            when {
                allOf {
                    branch 'ai/master'
                    environment name: 'HAS_DEPS',       value: 'true'
                    environment name: 'HAS_DOCKERFILE', value: 'true'
                }
            }
            steps { sh 'docker build -f docker/Dockerfile -t $IMAGE:latest -t $IMAGE:$BUILD_NUMBER .' }
        }

        stage('Deploy') {
            when {
                allOf {
                    branch 'ai/master'
                    environment name: 'HAS_DEPS',       value: 'true'
                    environment name: 'HAS_DOCKERFILE', value: 'true'
                }
            }
            steps {
                sh 'flock -w 900 /var/jenkins_home/cf-deploy.lock bash $DEPLOY_DIR/scripts/deploy-ai.sh'
            }
        }
    }

    post {
        success {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh success'
            echo "ai OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        failure {
            sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh failed'
            echo "ai FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}"
        }
        aborted { sh 'bash /home/ubuntu/deploy/scripts/gitlab-status.sh canceled' }
        // cleanup 은 선언적 파이프라인에서 항상 마지막이라 위 sh 들이 먼저 돈다
        cleanup { deleteDir() }
    }
}
