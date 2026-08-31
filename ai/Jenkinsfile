// AI Server CI/CD (Jenkins Multibranch Pipeline)
//
// 이 브랜치에는 아직 소스가 없다. 소스가 없으면 "실패"가 아니라 "스킵"이다.
// 파일만 추가하면 파이프라인이 알아서 켜지므로 인프라에 따로 요청할 것이 없다:
//   requirements.txt 또는 pyproject.toml 추가 → Test 스테이지 자동 활성화
//   tests/ 디렉터리 추가                       → pytest 자동 실행
//   위 + Dockerfile + ai/master push          → 이미지 빌드 + EC2 자동 배포
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
        stage('Detect') {
            steps {
                script {
                    env.HAS_DEPS       = (fileExists('requirements.txt') || fileExists('pyproject.toml')) ? 'true' : 'false'
                    env.HAS_TESTS      = (fileExists('tests') || fileExists('test')) ? 'true' : 'false'
                    env.HAS_DOCKERFILE = fileExists('Dockerfile') ? 'true' : 'false'
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
                    python3 -m venv .venv
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
            steps { sh 'docker build -t $IMAGE:latest -t $IMAGE:$BUILD_NUMBER .' }
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
        success { echo "ai OK — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        failure { echo "ai FAIL — ${env.BRANCH_NAME} #${env.BUILD_NUMBER}" }
        cleanup { deleteDir() }
    }
}
