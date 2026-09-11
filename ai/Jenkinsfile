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
                    # docker/Dockerfile 이 python:3.12-slim 을 쓴다.
                    # rapidocr-onnxruntime 이 3.13+ 을 지원하지 않으므로 에이전트 기본 python3 대신 3.12 를 명시한다.
                    command -v python3.12 >/dev/null 2>&1 || { echo "python3.12 가 에이전트에 없습니다. 설치가 필요합니다."; exit 1; }
                    python3.12 -m venv .venv
                    . .venv/bin/activate
                    python -m pip install --upgrade pip
                    if [ -f requirements.txt ]; then pip install -r requirements.txt; else pip install .; fi
                    if [ "$HAS_TESTS" = "true" ]; then
                        pip install pytest
                        python -m pytest -q
                    else
                        echo "SKIP: tests 디렉터리가 없어 테스트를 건너뜁니다."
                    fi
                '''
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
